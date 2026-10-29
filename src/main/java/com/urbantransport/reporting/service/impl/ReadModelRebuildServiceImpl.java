package com.urbantransport.reporting.service.impl;

import com.urbantransport.reporting.dto.RebuildResponse;
import com.urbantransport.reporting.exception.ApiException;
import com.urbantransport.reporting.exception.ErrorCodes;
import com.urbantransport.reporting.service.ReadModelRebuildService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadModelRebuildServiceImpl implements ReadModelRebuildService {

    private static final long KAFKA_TIMEOUT_SECONDS = 15;
    private static final List<String> READ_MODEL_TABLES = List.of(
            "dim_branch", "dim_bus", "dim_driver", "dim_route", "dim_depreciation_rate",
            "fact_trip", "fact_ticket_sale", "fact_rental", "fact_workshop_line", "fact_depreciation",
            "ledger_entries", "processed_events");

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
    private final KafkaAdmin kafkaAdmin;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Override
    public RebuildResponse rebuild() {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCodes.REBUILD_IN_PROGRESS,
                    "Ya hay una reconstrucción en curso");
        }
        try {
            log.warn("Reconstrucción de los modelos de lectura: deteniendo los consumidores del grupo {}", groupId);
            kafkaListenerEndpointRegistry.getListenerContainers().forEach(MessageListenerContainer::stop);

            int partitions = resetOffsetsToEarliest();
            transactionTemplate.executeWithoutResult(status -> entityManager
                    .createNativeQuery("TRUNCATE TABLE " + String.join(", ", READ_MODEL_TABLES))
                    .executeUpdate());

            log.warn("Modelos de lectura vaciados y {} particiones reiniciadas; se vuelven a leer los tópicos",
                    partitions);
            return new RebuildResponse(groupId, partitions, READ_MODEL_TABLES,
                    "Tablas vaciadas; los eventos se están volviendo a procesar desde el inicio");
        } finally {
            kafkaListenerEndpointRegistry.getListenerContainers().forEach(MessageListenerContainer::start);
            running.set(false);
        }
    }

    private int resetOffsetsToEarliest() {
        try (Admin admin = Admin.create(kafkaAdmin.getConfigurationProperties())) {
            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (committed.isEmpty()) {
                return 0;
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliest = admin
                    .listOffsets(committed.keySet().stream()
                            .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.earliest())))
                    .all()
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            admin.alterConsumerGroupOffsets(groupId, earliest.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    entry -> new OffsetAndMetadata(entry.getValue().offset()))))
                    .all()
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return earliest.size();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw kafkaUnavailable(exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw kafkaUnavailable(exception);
        }
    }

    private ApiException kafkaUnavailable(Exception exception) {
        log.error("No se pudieron reiniciar los offsets del grupo {}", groupId, exception);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.KAFKA_UNAVAILABLE,
                "No se pudo reiniciar la lectura de Kafka; las tablas no se modificaron");
    }
}
