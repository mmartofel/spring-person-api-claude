ALTER TABLE person
    ADD COLUMN street         VARCHAR(120),
    ADD COLUMN postal_code    VARCHAR(16),
    ADD COLUMN city           VARCHAR(80),
    ADD COLUMN country_code   VARCHAR(2),
    ADD COLUMN status         VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE',
    ADD COLUMN available_from DATE,
    ADD COLUMN available_to   DATE,
    ADD CONSTRAINT ck_person_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE')),
    ADD CONSTRAINT ck_person_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    ADD CONSTRAINT ck_person_available_range CHECK (available_from <= available_to);
