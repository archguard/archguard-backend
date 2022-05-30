CREATE TABLE `aac_dsl`
(
    `id`           BIGINT        AUTO_INCREMENT PRIMARY KEY,
    `description`  VARCHAR(1024) NOT NULL DEFAULT '',
    `content`      MEDIUMTEXT    NOT NULL,
    `title`        VARCHAR(1024) NOT NULL DEFAULT '',
    `author`       VARCHAR(1024) NOT NULL DEFAULT '',
    `created_at`   TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `publish_at`   TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);
