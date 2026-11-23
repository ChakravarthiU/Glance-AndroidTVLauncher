const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1200, height: 340 }, deviceScaleFactor: 1 });
  await p.goto('file://' + process.cwd() + '/diwali-static.html');
  await p.waitForTimeout(200);
  await p.screenshot({ path: 'diwali-art.png', omitBackground: true });
  await b.close();
})();
