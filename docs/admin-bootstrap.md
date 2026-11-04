# Admin bootstrap

The bootstrap endpoint promotes an existing account to `ROLE_ADMIN` once. It is
disabled until `ADMIN_BOOTSTRAP_SECRET` is configured and returns a conflict
after an administrator already exists. Normal admin operations use the
authenticated admin APIs; the bootstrap secret is never part of the Android app.

Generate and set a secret in the backend process environment:

```sh
export ADMIN_BOOTSTRAP_SECRET="$(openssl rand -hex 32)"
```

Start or restart the backend with that environment variable, then call the
bootstrap endpoint using the account's existing email:

```sh
curl --fail-with-body --request POST 'http://localhost:8080/api/v1/admin/bootstrap' \
  --header "X-Admin-Bootstrap-Secret: ${ADMIN_BOOTSTRAP_SECRET}" \
  --header 'Content-Type: application/json' \
  --data '{"email":"admin@example.com"}'
```

Keep the generated value in the deployment secret manager. Do not commit it,
place it in Android configuration, or include it in application logs. After
the promotion succeeds, sign in again to obtain an access token with the new
role. The bootstrap endpoint cannot be used to change roles after an admin
exists.

Admin dashboard, user, workout-plan, and workout-session endpoints require an
authenticated `ROLE_ADMIN` principal. User role changes support the existing
`ROLE_USER` and `ROLE_ADMIN` roles; coach and client status remains represented
by their existing profiles.
