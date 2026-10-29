ALTER TABLE fact_rental ADD COLUMN origin_text VARCHAR(200);
ALTER TABLE fact_rental ADD COLUMN destination_text VARCHAR(200);
ALTER TABLE fact_rental ADD COLUMN bus_id UUID;
