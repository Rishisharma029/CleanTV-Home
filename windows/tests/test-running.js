const http = require('http');

http.get('http://127.0.0.1:59822/index.html', r => {
  let d = '';
  r.on('data', c => d += c);
  r.on('end', () => {
    const m = d.match(/content="([a-f0-9]{32})"/);
    const tok = m ? m[1] : '';
    console.log('Server token:', tok);

    http.get('http://127.0.0.1:59822/api/key?device=127.0.0.1:5555&key=24&token=' + tok, res => {
      let k = '';
      res.on('data', c => k += c);
      res.on('end', () => {
        console.log('Key response for 127.0.0.1:5555:', k);
      });
    });

    http.get('http://127.0.0.1:59822/api/key?device=192.168.0.159:5555&key=24&token=' + tok, res => {
      let k = '';
      res.on('data', c => k += c);
      res.on('end', () => {
        console.log('Key response for 192.168.0.159:5555:', k);
      });
    });
  });
}).on('error', e => console.error(e));
