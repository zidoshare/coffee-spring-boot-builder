package site.zido.coffee.auth.handlers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface DisabledUserHandler {
    void handle(HttpServletRequest request, HttpServletResponse response) throws IOException;
}