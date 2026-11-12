package de.brickforceaurora.devtools.util.github;

import java.net.HttpURLConnection;

import me.lauriichan.snowframe.util.http.IHttpAuthenticator;

final class GithubAuthenticator implements IHttpAuthenticator {

    private final String token;

    public GithubAuthenticator(String token) {
        this.token = token;
    }

    @Override
    public void authenticate(final HttpURLConnection connection) {
        if (token == null || token.isBlank()) {
            return;
        }
        connection.setRequestProperty("Authorization", "Bearer " + token);
    }

}
