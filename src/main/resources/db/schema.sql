-- webflux.user_entity definition

CREATE TABLE `user_entity` (
                               `user_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                               `user_name` varchar(20) NOT NULL,
                               `password` varchar(100) NOT NULL,
                               `subscription_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                               `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                               `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                               PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- webflux.user_authorities definition

CREATE TABLE `user_authorities` (
                                    `user_id` varchar(100) NOT NULL,
                                    `authority` varchar(100) NOT NULL,
                                    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- webflux.user_contents_following definition

CREATE TABLE `user_contents_following` (
                                           `user_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                                           `contents_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                                           `following_seq` int NOT NULL,
                                           `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                           PRIMARY KEY (`user_id`,`contents_id`),
                                           KEY `idx_user_following_seq` (`user_id`,`following_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.user_contents_recommend definition

CREATE TABLE `user_contents_recommend` (
                                           `user_id` varchar(50) NOT NULL,
                                           `recommend_seq` int NOT NULL,
                                           `description` varchar(255) DEFAULT NULL,
                                           `contents_type` varchar(10) DEFAULT NULL,
                                           `folder_id` int DEFAULT NULL,
                                           `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                           PRIMARY KEY (`user_id`,`recommend_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- webflux.animation_folder_tree_entity definition

CREATE TABLE `animation_folder_tree_entity` (
                                                `folder_id` int NOT NULL AUTO_INCREMENT,
                                                `name` varchar(255) NOT NULL,
                                                `folder_path` varchar(500) NOT NULL,
                                                `parent_folder_id` int DEFAULT '0',
                                                `subscription_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                                                `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                                `modified_at` datetime DEFAULT NULL,
                                                `has_files` tinyint(1) NOT NULL DEFAULT '0',
                                                `contents_id` int DEFAULT NULL,
                                                PRIMARY KEY (`folder_id`),
                                                KEY `idx_parent_folder` (`parent_folder_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3616 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.drama_folder_tree_entity definition

CREATE TABLE `drama_folder_tree_entity` (
                                            `folder_id` int NOT NULL AUTO_INCREMENT,
                                            `name` varchar(255) NOT NULL,
                                            `folder_path` varchar(500) NOT NULL,
                                            `parent_folder_id` int DEFAULT '0',
                                            `subscription_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                                            `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                            `modified_at` datetime DEFAULT NULL,
                                            `has_files` tinyint(1) NOT NULL DEFAULT '0',
                                            `contents_id` int DEFAULT NULL,
                                            PRIMARY KEY (`folder_id`),
                                            KEY `idx_parent_folder` (`parent_folder_id`)
) ENGINE=InnoDB AUTO_INCREMENT=205 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.movie_folder_tree_entity definition

CREATE TABLE `movie_folder_tree_entity` (
                                            `folder_id` int NOT NULL AUTO_INCREMENT,
                                            `name` varchar(255) NOT NULL,
                                            `folder_path` varchar(500) NOT NULL,
                                            `parent_folder_id` int DEFAULT '0',
                                            `subscription_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                                            `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                            `modified_at` datetime DEFAULT NULL,
                                            `has_files` tinyint(1) NOT NULL DEFAULT '0',
                                            `contents_id` int DEFAULT NULL,
                                            PRIMARY KEY (`folder_id`),
                                            KEY `idx_parent_folder` (`parent_folder_id`)
) ENGINE=InnoDB AUTO_INCREMENT=492 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- webflux.contents_file_entity definition

CREATE TABLE `contents_file_entity` (
                                        `file_id` int NOT NULL AUTO_INCREMENT COMMENT '파일 ID',
                                        `file_name` varchar(255) NOT NULL,
                                        `file_path` varchar(1000) NOT NULL,
                                        `contents_id` int DEFAULT NULL COMMENT '컨텐츠 ID',
                                        `subtitle_path` varchar(1000) DEFAULT NULL,
                                        `resolution` varchar(50) DEFAULT NULL,
                                        `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        `subtitle_created_at` datetime DEFAULT NULL COMMENT '자막 추가 시간',
                                        PRIMARY KEY (`file_id`),
                                        KEY `idx_contents_file_id` (`contents_id`,`file_name`)
) ENGINE=InnoDB AUTO_INCREMENT=48766 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.contents_keywords definition

CREATE TABLE `contents_keywords` (
                                     `keyword_id` bigint NOT NULL AUTO_INCREMENT,
                                     `series_id` varchar(50) NOT NULL,
                                     `keyword` varchar(255) NOT NULL,
                                     PRIMARY KEY (`keyword_id`),
                                     KEY `idx_series_id` (`series_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.contents_object_entity definition

CREATE TABLE `contents_object_entity` (
                                          `contents_id` int NOT NULL AUTO_INCREMENT COMMENT '컨텐츠 ID',
                                          `title` varchar(100) DEFAULT NULL COMMENT '컨텐츠 이름',
                                          `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '컨텐츠 설명',
                                          `type` varchar(10) DEFAULT NULL COMMENT '컨텐츠 타입',
                                          `folder_id` int DEFAULT NULL COMMENT '컨텐츠 폴더 ID',
                                          `release_ym` varchar(6) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                                          `thumbnail_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '썸네일 url',
                                          `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                          `modified_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          `poster_url` varchar(500) DEFAULT NULL COMMENT '포스터 url',
                                          `subscription_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                                          `series_id` varchar(50) DEFAULT NULL COMMENT 'TMDB 시리즈 ID',
                                          `season` varchar(4) DEFAULT NULL COMMENT 'TMDB 시리즈의 해당 시즌',
                                          `useYn` varchar(1) DEFAULT 'Y' COMMENT '해당 컨텐츠 사용 여부',
                                          `confirmYn` varchar(1) DEFAULT 'N' COMMENT '해당 컨텐츠 확정 여부',
                                          PRIMARY KEY (`contents_id`),
                                          KEY `idx_modified_at` (`modified_at`),
                                          KEY `contents_object_entity_series_id_IDX` (`series_id`,`season`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=3890 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.featured_banners definition

CREATE TABLE `featured_banners` (
                                    `sequence_id` int NOT NULL AUTO_INCREMENT,
                                    `contents_id` int NOT NULL,
                                    `title` varchar(500) NOT NULL,
                                    `description` varchar(2000) DEFAULT NULL,
                                    `type` varchar(10) DEFAULT NULL,
                                    `user_rating` decimal(3,1) DEFAULT '0.0',
                                    `poster_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
                                    `thumbnail_url` varchar(500) DEFAULT NULL,
                                    `series_id` varchar(50) DEFAULT NULL,
                                    `season` varchar(4) DEFAULT NULL,
                                    `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
                                    `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                    PRIMARY KEY (`sequence_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.folder_tree_entity definition

CREATE TABLE `folder_tree_entity` (
                                      `folder_id` int NOT NULL AUTO_INCREMENT,
                                      `name` varchar(255) NOT NULL,
                                      `folder_path` varchar(500) NOT NULL,
                                      `parent_folder_id` int DEFAULT '0',
                                      `subscription_code` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                                      `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                      `modified_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      `has_files` tinyint(1) NOT NULL DEFAULT '0',
                                      `contents_id` int DEFAULT NULL,
                                      PRIMARY KEY (`folder_id`),
                                      KEY `idx_parent_folder` (`parent_folder_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10006 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.jwt_refresh_token_entity definition

CREATE TABLE `jwt_refresh_token_entity` (
                                            `session_code` char(36) NOT NULL,
                                            `user_id` varchar(255) NOT NULL,
                                            `issued_at` datetime NOT NULL,
                                            `expired_at` datetime NOT NULL,
                                            PRIMARY KEY (`session_code`),
                                            KEY `jwt_refresh_token_entity_user_id_IDX` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- webflux.registration_codes definition

CREATE TABLE `registration_codes` (
                                      `id` bigint NOT NULL AUTO_INCREMENT COMMENT '기본키',
                                      `reg_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '등록 코드',
                                      `code_description` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '코드 설명',
                                      `subscription_code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '가입 구독 코드',
                                      `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '활성화 여부',
                                      `max_usage_count` int DEFAULT NULL COMMENT '최대 사용 가능 횟수 (NULL=무제한)',
                                      `current_usage_count` int NOT NULL DEFAULT '0' COMMENT '현재 사용된 횟수',
                                      `valid_from` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '유효 시작 일시',
                                      `valid_until` datetime NOT NULL COMMENT '유효 종료 일시 (무기한일 시 9999년으로 설정)',
                                      `created_by` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '생성자',
                                      `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
                                      `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
                                      PRIMARY KEY (`id`),
                                      UNIQUE KEY `uk_registration_codes_code` (`reg_code`),
                                      KEY `idx_registration_codes_valid_period` (`valid_until`,`valid_from`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='회원가입 등록 코드 관리';