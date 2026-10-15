package com.logviewer.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.logviewer.domain.SessionNotFoundException;
import com.logviewer.session.PathNotAllowedException;
import com.logviewer.store.StoreException;

/** Maps domain failures to RFC 9457 problem responses. */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(SessionNotFoundException.class)
	ProblemDetail notFound(SessionNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(PathNotAllowedException.class)
	ProblemDetail forbidden(PathNotAllowedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail tooLarge(MaxUploadSizeExceededException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The file exceeds the upload limit");
	}

	@ExceptionHandler(StoreException.class)
	ProblemDetail storage(StoreException ex) {
		log.error("Storage failure", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Storage error");
	}
}
