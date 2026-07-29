package com.orbisflow.requests.application;

import com.orbisflow.auth.domain.JwtService.AuthenticatedUser;
import com.orbisflow.common.errors.ApiErrorCode;
import com.orbisflow.common.errors.ApiException;
import com.orbisflow.documents.persistence.DocumentRepository;
import com.orbisflow.requests.api.RequestDtos.RequestDetail;
import com.orbisflow.requests.domain.Request;
import com.orbisflow.requests.domain.RequestStatus;
import com.orbisflow.requests.persistence.ExtractedInvoiceDataRepository;
import com.orbisflow.requests.persistence.RequestRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RequestQueryService {
    private final RequestRepository requests;
    private final ExtractedInvoiceDataRepository extractedData;
    private final DocumentRepository documents;

    public RequestQueryService(
            RequestRepository requests,
            ExtractedInvoiceDataRepository extractedData,
            DocumentRepository documents) {
        this.requests = requests;
        this.extractedData = extractedData;
        this.documents = documents;
    }

    public RequestDetail get(AuthenticatedUser principal, UUID requestId) {
        Request request = requireScoped(principal, requestId);
        return detail(request);
    }

    public Request requireScoped(AuthenticatedUser principal, UUID requestId) {
        Request request = requests.findById(requestId).orElseThrow(
                RequestQueryService::notFound);
        boolean allowed = switch (principal.role()) {
            case EMPLOYEE -> request.employeeId().equals(principal.id());
            case MANAGER -> request.managerId().equals(principal.id())
                    && isPostRouting(request.status());
            case FINANCE -> request.status() == RequestStatus.FINANCE_REVIEW
                    || request.status() == RequestStatus.PROCESSED;
        };
        if (!allowed) {
            throw notFound();
        }
        return request;
    }

    public RequestDetail detail(Request request) {
        return RequestDetail.from(
                request,
                extractedData.findByRequestId(request.id()).orElse(null),
                documents.findCurrentByRequestId(request.id()).orElse(null));
    }

    public static boolean isPostRouting(RequestStatus status) {
        return status == RequestStatus.MANAGER_REVIEW
                || status == RequestStatus.REJECTED
                || status == RequestStatus.FINANCE_REVIEW
                || status == RequestStatus.PROCESSED;
    }

    public static ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.RESOURCE_NOT_FOUND,
                "The requested resource was not found.");
    }
}
