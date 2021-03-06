package site.zido.coffee.mvc.rest;


import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import site.zido.coffee.mvc.exceptions.CommonBusinessException;

import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolationException;

/**
 * server 异常处理,兜底处理
 *
 * @author zido
 */
@RestControllerAdvice
@Order
public class GlobalExceptionAdvice extends BaseGlobalExceptionHandler {
    public GlobalExceptionAdvice(HttpResponseBodyFactory factory) {
        super(factory);
    }

    /**
     * parameter参数校验异常处理
     *
     * @param e 校验异常
     * @return result
     */
    @ExceptionHandler(value = ConstraintViolationException.class)
    @Override
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException e, WebRequest request) {
        return super.handleConstraintViolationException(e, request);
    }

    @ExceptionHandler(CommonBusinessException.class)
    @Override
    protected ResponseEntity<Object> handleCommonBusinessException(CommonBusinessException e, WebRequest request, HttpServletResponse response) {
        return super.handleCommonBusinessException(e, request, response);
    }

}
