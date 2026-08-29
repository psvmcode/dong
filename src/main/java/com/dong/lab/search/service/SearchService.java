package com.dong.lab.search.service;

import com.dong.lab.search.dto.ProductSearchRequest;
import com.dong.lab.search.dto.ProductSearchResponse;
import com.dong.lab.search.entity.ProductDocument;

public interface SearchService {

    void index(ProductDocument document);

    void bulkIndex(Iterable<ProductDocument> documents);

    void deleteById(String id);

    ProductSearchResponse search(ProductSearchRequest request);

    long count();

}
