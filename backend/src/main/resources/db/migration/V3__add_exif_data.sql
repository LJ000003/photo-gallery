CREATE TABLE exif_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    photo_id BIGINT NOT NULL UNIQUE,
    date_taken DATETIME(6),
    camera_model VARCHAR(255),
    lens_model VARCHAR(255),
    focal_length VARCHAR(50),
    aperture VARCHAR(50),
    shutter_speed VARCHAR(50),
    iso INT,
    latitude DOUBLE,
    longitude DOUBLE,
    altitude DOUBLE,
    FOREIGN KEY (photo_id) REFERENCES photos(id) ON DELETE CASCADE
);
