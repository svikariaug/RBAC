CREATE INDEX IF NOT EXISTS idx_notification_tasks_pending_id
    ON notification_tasks (status, id)
    WHERE status = 'PENDING';
