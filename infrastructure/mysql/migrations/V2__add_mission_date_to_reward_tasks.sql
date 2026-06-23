-- Migration: add mission_date column to reward_tasks
-- Apply manually: docker exec -i payflow-mysql mysql -u<USER> -p<PASS> payflow_reward < this_file.sql

ALTER TABLE reward_tasks
    ADD COLUMN mission_date DATE NULL;
