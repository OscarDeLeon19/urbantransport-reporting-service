CREATE TABLE dim_branch (
                            branch_id   UUID PRIMARY KEY,
                            name        VARCHAR(100),
                            code        VARCHAR(20),
                            latitude    NUMERIC(9,6),
                            longitude   NUMERIC(9,6),
                            active      BOOLEAN,
                            updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dim_bus (
                         bus_id             UUID PRIMARY KEY,
                         branch_id          UUID,
                         plate              VARCHAR(15),
                         brand              VARCHAR(60),
                         model              VARCHAR(60),
                         manufacture_year   SMALLINT,
                         capacity           INTEGER,
                         status             VARCHAR(20),
                         odometer_km        NUMERIC(12,2),
                         current_driver_id  UUID,
                         updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dim_driver (
                            driver_id           UUID PRIMARY KEY,
                            user_id             UUID,
                            branch_id           UUID,
                            full_name           VARCHAR(150),
                            license_number      VARCHAR(30),
                            license_type        VARCHAR(2),
                            license_expires_on  DATE,
                            active              BOOLEAN,
                            updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dim_route (
                           route_id               UUID PRIMARY KEY,
                           origin_branch_id       UUID,
                           destination_branch_id  UUID,
                           distance_km            NUMERIC(8,2),
                           ticket_price           NUMERIC(10,2),
                           deleted                BOOLEAN,
                           updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE dim_depreciation_rate (
                                       rate_id         UUID PRIMARY KEY,
                                       amount_per_km   NUMERIC(10,4),
                                       effective_from  TIMESTAMPTZ
);

CREATE TABLE fact_trip (
                           trip_id                UUID PRIMARY KEY,
                           type                   VARCHAR(10),
                           route_id               UUID,
                           rental_id              UUID,
                           origin_branch_id       UUID,
                           destination_branch_id  UUID,
                           bus_id                 UUID,
                           driver_id              UUID,
                           scheduled_departure    TIMESTAMPTZ,
                           actual_departure_at    TIMESTAMPTZ,
                           actual_arrival_at      TIMESTAMPTZ,
                           km_traveled            NUMERIC(12,2),
                           fuel_cost              NUMERIC(10,2),
                           driver_pay             NUMERIC(10,2),
                           ticket_price           NUMERIC(10,2),
                           status                 VARCHAR(20),
                           updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fact_ticket_sale (
                                  ticket_id     UUID PRIMARY KEY,
                                  order_id      UUID,
                                  trip_id       UUID,
                                  route_id      UUID,
                                  bus_id        UUID,
                                  branch_id     UUID,
                                  channel       VARCHAR(20),
                                  seat_number   INTEGER,
                                  price_paid    NUMERIC(10,2),
                                  payment_date  DATE,
                                  cancelled     BOOLEAN NOT NULL DEFAULT FALSE,
                                  cancelled_at  TIMESTAMPTZ
);

CREATE TABLE fact_rental (
                             rental_id       UUID PRIMARY KEY,
                             branch_id       UUID,
                             channel         VARCHAR(10),
                             customer_nit    VARCHAR(20),
                             amount          NUMERIC(10,2),
                             payment_date    DATE,
                             departure_date  DATE,
                             return_date     DATE,
                             passengers      INTEGER,
                             distance_km     NUMERIC(8,2),
                             status          VARCHAR(20),
                             updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fact_workshop_line (
                                    line_id        UUID PRIMARY KEY,
                                    expense_id     UUID,
                                    bus_id         UUID,
                                    branch_id      UUID,
                                    service_date   DATE,
                                    category_id    UUID,
                                    category_name  VARCHAR(80),
                                    type           VARCHAR(10),
                                    description    VARCHAR(255),
                                    amount         NUMERIC(12,2)
);

CREATE TABLE fact_depreciation (
                                   trip_id       UUID PRIMARY KEY,
                                   bus_id        UUID,
                                   branch_id     UUID,
                                   km            NUMERIC(12,2),
                                   rate_id       UUID,
                                   rate_applied  NUMERIC(10,4),
                                   amount        NUMERIC(12,2),
                                   arrived_at    TIMESTAMPTZ
);

CREATE TABLE ledger_entries (
                                id           UUID PRIMARY KEY,
                                type         VARCHAR(10)   NOT NULL,
                                concept      VARCHAR(20)   NOT NULL,
                                branch_id    UUID,
                                amount       NUMERIC(12,2) NOT NULL,
                                occurred_on  DATE          NOT NULL,
                                source_type  VARCHAR(30)   NOT NULL,
                                source_id    UUID          NOT NULL,
                                description  VARCHAR(255),
                                created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                UNIQUE (source_type, source_id)
);

CREATE TABLE processed_events (
                                  event_id      UUID PRIMARY KEY,
                                  event_type    VARCHAR(80) NOT NULL,
                                  processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
