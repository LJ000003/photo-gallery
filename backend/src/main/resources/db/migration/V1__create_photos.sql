CREATE TABLE photos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    file_name VARCHAR(255),
    original_file_name VARCHAR(255),
    file_size BIGINT,
    content_type VARCHAR(255),
    created_at DATETIME(6)
);
