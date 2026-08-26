DROP TABLE IF EXISTS image;
DROP TABLE IF EXISTS album;

CREATE TABLE album (
    id           UUID PRIMARY KEY,
    album_name   VARCHAR(255),
    city_name    VARCHAR(255),
    country_name VARCHAR(255),
    lat          DOUBLE PRECISION,
    lang         DOUBLE PRECISION
);

CREATE INDEX idx_album_lat_lang ON album (lat, lang);
 
CREATE TABLE image (
    id       UUID PRIMARY KEY,
    img      VARCHAR(255),
    album_id UUID,
    CONSTRAINT fk_image_album FOREIGN KEY (album_id) REFERENCES album (id)
);