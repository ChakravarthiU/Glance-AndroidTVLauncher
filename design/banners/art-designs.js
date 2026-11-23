// Renders banner-art.html for each design to <design>-art.png (transparent). Then:
//   python3 -c "from PIL import Image; [Image.open(f'{d}-art.png').save(f'{d}-art.webp','WEBP',quality=85,method=6) for d in ('simple','birthday','christmas')]"
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 340 }, deviceScaleFactor: 1 });
  for (const d of process.argv.slice(2).length ? process.argv.slice(2) : ['simple', 'birthday', 'christmas']) {
    await p.goto('file://' + process.cwd() + '/banner-art.html?design=' + d);
    await p.waitForTimeout(200);
    await p.screenshot({ path: d + '-art.png', omitBackground: true });
  }
  await b.close();
})();
