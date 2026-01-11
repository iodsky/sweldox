ALTER TABLE employment_details
    ADD start_shift TIME NOT NULL DEFAULT '08:00:00',
    ADD end_shift TIME NOT NULL DEFAULT '17:00:00';
