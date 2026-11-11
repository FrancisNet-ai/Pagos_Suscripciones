-- Redes sociales del vendedor: opcionales; la tienda muestra solo el ícono de las que tenga.
ALTER TABLE businesses
    ADD COLUMN instagram_url VARCHAR(300),
    ADD COLUMN facebook_url  VARCHAR(300),
    ADD COLUMN tiktok_url    VARCHAR(300),
    ADD COLUMN website_url   VARCHAR(300);
