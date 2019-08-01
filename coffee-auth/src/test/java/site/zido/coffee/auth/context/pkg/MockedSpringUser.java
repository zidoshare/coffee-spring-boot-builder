package site.zido.coffee.auth.context.pkg;

import org.springframework.data.annotation.Id;
import site.zido.coffee.auth.entity.IUser;

public class MockedSpringUser implements IUser {
    private static final long serialVersionUID = -200831662244124675L;
    @Id
    private Integer id;
    private String username;
    private String password;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}