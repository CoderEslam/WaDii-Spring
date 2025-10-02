package com.doubleclick.wadii.ts;

import com.doubleclick.wadii.utils.Response;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface DatabaseOperation<R, L, ID> {

    ResponseEntity<Response<R>> show(Long id);

    ResponseEntity<Response<R>> create(L l);

    ResponseEntity<Response<R>> update(L l);

    ResponseEntity<Response<R>> delete(ID id);

    ResponseEntity<Response<List<R>>> readAll();

}
