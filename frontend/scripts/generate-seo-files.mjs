import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const rawSiteUrl = process.env.WWTRADING_SITE_URL?.trim();
if (!rawSiteUrl) {
  throw new Error('WWTRADING_SITE_URL is required to generate production robots.txt and sitemap.xml.');
}

const siteUrl = new URL(rawSiteUrl);
if (
  siteUrl.protocol !== 'https:'
  || siteUrl.pathname !== '/'
  || siteUrl.username
  || siteUrl.password
  || siteUrl.search
  || siteUrl.hash
) {
  throw new Error('WWTRADING_SITE_URL must be an HTTPS origin without a path.');
}

const origin = siteUrl.origin;
const publicPaths = [...new Set([
  '/',
  '/products',
  '/services/vente-machines',
  '/services/location',
  '/services/maintenance',
  '/services/toners-consommables',
  '/services/pieces-rechange',
  '/services/support-technique',
  '/contact',
  '/devis'
])];

const xml = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
${publicPaths.map((path) => `  <url><loc>${escapeXml(`${origin}${path}`)}</loc></url>`).join('\n')}
</urlset>
`;

const robots = `User-agent: *
Allow: /
Disallow: /admin/
Disallow: /manager/
Disallow: /commercial/
Disallow: /technician/
Disallow: /client/
Disallow: /account/
Disallow: /notifications
Disallow: /orders
Disallow: /cart
Disallow: /wishlist
Disallow: /checkout
Disallow: /addresses
Disallow: /login
Disallow: /register
Disallow: /forgot-password
Disallow: /access-denied
Disallow: /devis/mes-demandes

Sitemap: ${origin}/sitemap.xml
`;

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'public');
await Promise.all([
  writeFile(resolve(root, 'sitemap.xml'), xml, 'utf8'),
  writeFile(resolve(root, 'robots.txt'), robots, 'utf8')
]);

console.log(`SEO files generated for ${origin}`);

function escapeXml(value) {
  return value.replace(/[<>&'\"]/g, (character) => ({
    '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;'
  })[character]);
}
