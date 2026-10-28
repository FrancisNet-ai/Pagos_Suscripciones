import { smallImage } from './small-image.pipe';

describe('smallImage', () => {
  it('pide la versión reducida solo de imágenes propias', () => {
    expect(smallImage('/api/v1/public/media/abc.png')).toBe('/api/v1/public/media/abc.png?size=sm');
    expect(smallImage('https://otro.sitio/foto.png')).toBe('https://otro.sitio/foto.png');
    expect(smallImage(null)).toBeNull();
  });
});
