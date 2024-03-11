package org.lucoenergia.conluz.domain.admin.datadis;

public class DatadisConfig {

    private String username;
    private String password;

    public DatadisConfig(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
