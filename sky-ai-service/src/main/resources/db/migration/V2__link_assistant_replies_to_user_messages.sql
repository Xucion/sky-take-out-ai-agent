-- 为聊天请求补充完整幂等关联：请求指纹防止同一幂等键复用不同载荷，
-- reply_to_message_id 保证一条用户消息最多创建一条助手回复。

ALTER TABLE `ai_message`
    ADD COLUMN `request_fingerprint` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '用户聊天请求的SHA-256指纹，用于识别幂等键冲突'
        AFTER `client_request_id`,
    ADD COLUMN `reply_to_message_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '助手消息所回复的用户消息ID'
        AFTER `request_fingerprint`,
    ADD UNIQUE KEY `uk_ai_message_reply_to_message` (`reply_to_message_id`),
    ADD CONSTRAINT `fk_ai_message_reply_to_message`
        FOREIGN KEY (`reply_to_message_id`) REFERENCES `ai_message` (`message_id`)
        ON UPDATE RESTRICT ON DELETE RESTRICT;
