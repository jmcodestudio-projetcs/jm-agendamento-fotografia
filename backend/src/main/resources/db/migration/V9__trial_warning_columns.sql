BEGIN;
SET LOCAL search_path = scheduling, public;

ALTER TABLE subscriptions
    ADD COLUMN trial_warning_7d_sent_at timestamptz,
    ADD COLUMN trial_warning_1d_sent_at timestamptz;

COMMIT;