package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.dto.RebuildResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadModelRebuildServiceImplTest {

    private static final String GROUP_ID = "reporting-service";
    private static final Map<String, Object> ADMIN_CONFIG = Map.of("bootstrap.servers", "localhost:9092");
    private static final TopicPartition TRIP_PARTITION = new TopicPartition("trip-events", 0);
    private static final TopicPartition FLEET_PARTITION = new TopicPartition("fleet-events", 2);
    private static final String TRUNCATE = "TRUNCATE TABLE dim_branch, dim_bus, dim_driver, dim_route, "
            + "dim_depreciation_rate, fact_trip, fact_ticket_sale, fact_rental, fact_workshop_line, "
            + "fact_depreciation, ledger_entries, processed_events";

    @Mock
    private KafkaListenerEndpointRegistry registry;

    @Mock
    private KafkaAdmin kafkaAdmin;

    @Mock
    private EntityManager entityManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private MessageListenerContainer tripContainer;

    @Mock
    private MessageListenerContainer fleetContainer;

    @Mock
    private Admin admin;

    @Mock
    private ListConsumerGroupOffsetsResult committedResult;

    @Mock
    private ListOffsetsResult earliestResult;

    @Mock
    private AlterConsumerGroupOffsetsResult alterResult;

    @Mock
    private Query truncateQuery;

    @Captor
    private ArgumentCaptor<Map<TopicPartition, OffsetSpec>> specCaptor;

    @Captor
    private ArgumentCaptor<Map<TopicPartition, OffsetAndMetadata>> offsetsCaptor;

    private MockedStatic<Admin> adminFactory;

    private ReadModelRebuildServiceImpl rebuildService;

    @BeforeEach
    void setUp() {
        rebuildService = new ReadModelRebuildServiceImpl(registry, kafkaAdmin, entityManager, transactionTemplate);
        ReflectionTestUtils.setField(rebuildService, "groupId", GROUP_ID);
        when(registry.getListenerContainers()).thenReturn(List.of(tripContainer, fleetContainer));
        when(kafkaAdmin.getConfigurationProperties()).thenReturn(ADMIN_CONFIG);
        adminFactory = mockStatic(Admin.class);
        adminFactory.when(() -> Admin.create(ADMIN_CONFIG)).thenReturn(admin);
    }

    @AfterEach
    void closeStaticMock() {
        adminFactory.close();
    }

    @Test
    void rebuild_stopsConsumersResetsOffsetsTruncatesAndRestarts() {
        stubCommittedOffsets(Map.of(TRIP_PARTITION, new OffsetAndMetadata(16), FLEET_PARTITION,
                new OffsetAndMetadata(7)));
        when(admin.listOffsets(anyMap())).thenReturn(earliestResult);
        when(earliestResult.all()).thenReturn(KafkaFuture.completedFuture(Map.of(
                TRIP_PARTITION, new ListOffsetsResult.ListOffsetsResultInfo(0, -1, Optional.empty()),
                FLEET_PARTITION, new ListOffsetsResult.ListOffsetsResultInfo(3, -1, Optional.empty()))));
        when(admin.alterConsumerGroupOffsets(any(), anyMap())).thenReturn(alterResult);
        when(alterResult.all()).thenReturn(KafkaFuture.completedFuture(null));
        stubTruncate();

        RebuildResponse response = rebuildService.rebuild();

        assertEquals(GROUP_ID, response.getConsumerGroup());
        assertEquals(2, response.getPartitionsReset());
        assertEquals(12, response.getClearedTables().size());
        assertTrue(response.getClearedTables().contains("ledger_entries"));
        assertTrue(response.getClearedTables().contains("processed_events"));

        verify(admin).listOffsets(specCaptor.capture());
        assertEquals(2, specCaptor.getValue().size());
        specCaptor.getValue().values().forEach(spec -> assertInstanceOf(OffsetSpec.EarliestSpec.class, spec));
        verify(admin).alterConsumerGroupOffsets(eq(GROUP_ID), offsetsCaptor.capture());
        assertEquals(0, offsetsCaptor.getValue().get(TRIP_PARTITION).offset());
        assertEquals(3, offsetsCaptor.getValue().get(FLEET_PARTITION).offset());

        InOrder order = inOrder(tripContainer, fleetContainer, admin, truncateQuery);
        order.verify(tripContainer).stop();
        order.verify(fleetContainer).stop();
        order.verify(admin).alterConsumerGroupOffsets(any(), anyMap());
        order.verify(truncateQuery).executeUpdate();
        order.verify(tripContainer).start();
        order.verify(fleetContainer).start();
        verify(admin).close();
    }

    @Test
    void groupWithoutCommittedOffsets_onlyTruncates() {
        stubCommittedOffsets(Map.of());
        stubTruncate();

        RebuildResponse response = rebuildService.rebuild();

        assertEquals(0, response.getPartitionsReset());
        verify(admin, never()).listOffsets(anyMap());
        verify(admin, never()).alterConsumerGroupOffsets(any(), anyMap());
        verify(truncateQuery).executeUpdate();
        verify(tripContainer).start();
    }

    @Test
    void kafkaFailure_returnsServiceUnavailableWithoutTruncatingAndRestartsConsumers() throws Exception {
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> failing = mock(KafkaFuture.class);
        when(failing.get(anyLong(), any(TimeUnit.class))).thenThrow(new ExecutionException(
                new IllegalStateException("broker caído")));
        when(admin.listConsumerGroupOffsets(GROUP_ID)).thenReturn(committedResult);
        when(committedResult.partitionsToOffsetAndMetadata()).thenReturn(failing);

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.KAFKA_UNAVAILABLE, rebuildService::rebuild);

        verifyNoInteractions(transactionTemplate, entityManager);
        verify(tripContainer).start();
        verify(fleetContainer).start();
    }

    @Test
    void kafkaTimeout_returnsServiceUnavailable() throws Exception {
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> slow = mock(KafkaFuture.class);
        when(slow.get(anyLong(), any(TimeUnit.class))).thenThrow(new TimeoutException("sin respuesta"));
        when(admin.listConsumerGroupOffsets(GROUP_ID)).thenReturn(committedResult);
        when(committedResult.partitionsToOffsetAndMetadata()).thenReturn(slow);

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.KAFKA_UNAVAILABLE, rebuildService::rebuild);

        verifyNoInteractions(entityManager);
    }

    @Test
    void interruptedWhileWaitingKafka_restoresInterruptFlag() throws Exception {
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> interrupted = mock(KafkaFuture.class);
        when(interrupted.get(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException("interrumpido"));
        when(admin.listConsumerGroupOffsets(GROUP_ID)).thenReturn(committedResult);
        when(committedResult.partitionsToOffsetAndMetadata()).thenReturn(interrupted);

        assertApiError(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.KAFKA_UNAVAILABLE, rebuildService::rebuild);

        assertTrue(Thread.interrupted());
        verifyNoInteractions(entityManager);
    }

    @Test
    void secondRebuildWhileRunning_isRejectedAndFirstOneFinishes() {
        stubCommittedOffsets(Map.of());
        stubTruncate();
        doAnswer(invocation -> {
            assertApiError(HttpStatus.CONFLICT, ErrorCodes.REBUILD_IN_PROGRESS, rebuildService::rebuild);
            return null;
        }).when(tripContainer).stop();

        RebuildResponse response = rebuildService.rebuild();

        assertEquals(0, response.getPartitionsReset());
        verify(truncateQuery).executeUpdate();
    }

    @Test
    void afterFailedRebuild_aNewRebuildCanRun() throws Exception {
        KafkaFuture<Map<TopicPartition, OffsetAndMetadata>> failing = mock(KafkaFuture.class);
        when(failing.get(anyLong(), any(TimeUnit.class))).thenThrow(new ExecutionException(
                new IllegalStateException("broker caído")));
        when(admin.listConsumerGroupOffsets(GROUP_ID)).thenReturn(committedResult);
        when(committedResult.partitionsToOffsetAndMetadata()).thenReturn(failing)
                .thenReturn(KafkaFuture.completedFuture(Map.of()));
        stubTruncate();

        assertThrows(ApiException.class, rebuildService::rebuild);
        RebuildResponse response = rebuildService.rebuild();

        assertEquals(0, response.getPartitionsReset());
    }

    private void stubCommittedOffsets(Map<TopicPartition, OffsetAndMetadata> offsets) {
        when(admin.listConsumerGroupOffsets(GROUP_ID)).thenReturn(committedResult);
        when(committedResult.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(offsets));
    }

    private void stubTruncate() {
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(entityManager.createNativeQuery(TRUNCATE)).thenReturn(truncateQuery);
    }

    private static void assertApiError(HttpStatus status, String code, Executable action) {
        ApiException exception = assertThrows(ApiException.class, action);
        assertEquals(status, exception.getStatus());
        assertEquals(code, exception.getCode());
    }
}
