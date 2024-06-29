package org.lucoenergia.conluz.infrastructure.shared.security.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.lucoenergia.conluz.domain.admin.user.auth.Token;
import org.springframework.stereotype.Component;

@Component
public class AuthResponseHandler {

    public void setAccessCookie(HttpServletResponse response, Token accessToken) {
        Cookie authCookie = new Cookie(AuthParameter.ACCESS_TOKEN.getCookieName(), accessToken.getToken());
        authCookie.setHttpOnly(true); // Setting a cookie as HttpOnly means that it cannot be accessed by client-side scripts. This measure can help to mitigate certain types of cross-site scripting (XSS) vulnerabilities.
        authCookie.setSecure(true); // If you are working with HTTPS
        authCookie.setPath("/"); // Determines the URLs to which the cookie will be sent by the browser. Setting the path to "/", the cookie will be sent to all URLs within the domain.
        /*
         * The SameSite attribute prevents the browser from sending this cookie along with cross-site requests.
         * With "Lax" the cookie will be sent when the user navigates to the cookie's origin site. For example, from a link on another site they're browsing, or by typing the URL of the site into the address bar. In other words, the cookie will be sent in a third-party context, but not during an initial page load.
         * With "None" will ensure that the cookie is sent with all requests, first and third party.
         */
        authCookie.setAttribute("SameSite", "Lax");

        // Add cookie to the response
        response.addCookie(authCookie);
    }
}
