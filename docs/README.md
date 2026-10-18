# ScreenVideo Hub deployment

## 1. Enable GitHub Pages

In the repository settings, open **Pages** and set the build/deployment source to **GitHub Actions**. Merge the site into `main`; the workflow at `.github/workflows/pages.yml` publishes the `docs/` directory.

Expected project URL: `https://marciogames-pixel.github.io/ScreenVideo-FREE/` (only after Pages is enabled and deployment succeeds).

## 2. Configure video storage

The site is static and cannot store uploads by itself. To enable real public share links:

1. Create a Supabase project.
2. Open the SQL Editor and review/run `supabase-setup.sql`.
3. In the published site, enter the project URL, the public anon/publishable key, and bucket name `videos`.
4. Press **Save in this browser** and try a short, non-private MP4.

Never enter a `service_role` or secret key in the website. The public-upload SQL intentionally makes uploads available to anyone who can access the page and key; this is only a prototype. Add authentication, quotas, moderation, and abuse prevention before promoting it publicly.

## Share links

After upload, the site creates a page URL with the public storage URL encoded in the query string. Anyone with the link can view the video if the bucket remains public. The preview of a local file is not a public upload until the upload succeeds.

## Privacy

Do not upload private recordings, passwords, personal documents, or videos of other people without permission. Public links may be forwarded.