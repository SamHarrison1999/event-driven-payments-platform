ALTER TABLE customer_profile
    ADD CONSTRAINT ck_customer_profile_version
        CHECK (version >= 0);