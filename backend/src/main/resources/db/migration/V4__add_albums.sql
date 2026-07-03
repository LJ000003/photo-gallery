CREATE TABLE albums (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    cover_photo_id BIGINT,
    created_at DATETIME(6)
);

CREATE TABLE photo_albums (
    photo_id BIGINT NOT NULL,
    album_id BIGINT NOT NULL,
    PRIMARY KEY (photo_id, album_id),
    FOREIGN KEY (photo_id) REFERENCES photos(id) ON DELETE CASCADE,
    FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE CASCADE
);
