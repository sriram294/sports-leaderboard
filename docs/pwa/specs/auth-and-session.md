# Auth and session

GIS returns a Google ID token only for the exchange endpoint. The API access token is sent as a Bearer token; refresh is attempted once per failed request. `localStorage` is the current portable browser baseline; production deployment must use HTTPS, tight CSP, and a documented XSS review. Logout revokes the refresh token and clears local state.
