package com.modle.domain.contract.service;

import com.modle.domain.application.entity.Application;
import com.modle.domain.application.entity.type.ApplicationStatus;
import com.modle.domain.application.repository.ApplicationRepository;
import com.modle.domain.application.service.ApplicationService;
import com.modle.domain.contract.dto.request.ContractCreateRequest;
import com.modle.domain.contract.dto.request.ContractPdfCreateRequest;
import com.modle.domain.contract.dto.response.*;
import com.modle.domain.contract.entity.Contract;
import com.modle.domain.contract.entity.type.ContractListStatus;
import com.modle.domain.contract.entity.type.ContractStatus;
import com.modle.domain.contract.repository.ContractRepository;
import com.modle.domain.contract.repository.ContractTemplateRepository;
import com.modle.domain.jobposting.dto.response.JobPostingResponse;
import com.modle.domain.jobposting.service.JobPostingService;
import com.modle.domain.user.entity.Model;
import com.modle.domain.user.entity.User;
import com.modle.domain.user.repository.ModelRepository;
import com.modle.domain.user.service.UserService;
import com.modle.global.exception.CustomException;
import com.modle.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractService {

    private final ContractRepository contractRepository;
    private final ContractTemplateRepository contractTemplateRepository;
    private final ApplicationRepository applicationRepository;

    private final ContractValidator contractValidator;
    private final ContractDocumentService contractDocumentService;
    private final ApplicationService applicationService;
    private final JobPostingService jobPostingService;
    private final UserService userService;
    private final ModelRepository modelRepository;
    private final ContractNotificationService contractNotificationService;
    private final ContractQueryService contractQueryService;

    @Transactional
    public ContractResponse createContract(Long clientUserId, ContractCreateRequest request) {
        Application application = applicationService.getApplication(request.applicationId());
        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        contractValidator.validateContractOwner(clientUserId, jobPosting.clientId());
        contractValidator.validateContractDraftableStatus(application);
        contractValidator.validateCreateRequest(request);

        return contractRepository.findByApplicationId(request.applicationId())
                .map(existingContract -> rewriteDraftableContract(existingContract, request))
                .orElseGet(() -> createNewContract(request));
    }

    @Transactional
    public ContractPdfResponse generatePdf(
            Long clientUserId,
            ContractPdfCreateRequest request,
            String clientIp
    ) {
        Contract contract = contractRepository.findById(request.contractId())
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        contractValidator.validateDraftStatus(contract);

        Application application = applicationService.getApplication(contract.getApplicationId());
        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        contractValidator.validateContractOwner(clientUserId, jobPosting.clientId());
        contractValidator.validateContractDraftableStatus(application);

        contract.clientAgree(LocalDateTime.now(), clientIp);

        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        User clientUser = userService.findById(jobPosting.clientId());
        User modelUser = userService.findById(model.getUser().getId());

        return contractDocumentService.generateDraftPdf(
                contract,
                jobPosting,
                clientUser,
                model,
                modelUser
        );
    }

    @Transactional
    public ContractResponse notifyContract(Long clientUserId, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        contractValidator.validateDraftStatus(contract);
        contractValidator.validatePdfReady(contract);

        Application application = applicationService.getApplication(contract.getApplicationId());
        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        contractValidator.validateContractOwner(clientUserId, jobPosting.clientId());
        contractValidator.validateContractNotifiableStatus(application);
        contractValidator.validateRequiredCount(application, jobPosting);

        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        User modelUser = userService.findById(model.getUser().getId());

        contractNotificationService.sendContractNotification(
                contract,
                application,
                clientUserId,
                modelUser
        );

        contract.notifyModel(LocalDateTime.now());
        applicationService.markContractSent(contract.getApplicationId());

        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse agreeContract(Long modelUserId, Long contractId, String modelIp) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        contractValidator.validateAgreeableStatus(contract);

        Application application = applicationService.getApplication(contract.getApplicationId());

        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        contractValidator.validateContractTargetModel(modelUserId, model.getUser().getId());

        contract.modelAgree(LocalDateTime.now(), modelIp);

        if (contract.isBothAgreed()) {
            confirmContract(contract, application);
        }

        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse rejectContract(Long modelUserId, Long contractId, String rejectReason) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        contractValidator.validateAgreeableStatus(contract);

        Application application = applicationService.getApplication(contract.getApplicationId());
        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        contractValidator.validateContractTargetModel(modelUserId, model.getUser().getId());

        contract.reject(rejectReason);
        application.revertToContacted(); // 거부 시 재계약 가능하도록 CONTACTED 상태로 롤백

        return ContractResponse.from(contract);
    }

    public ContractDraftResponse getDraftContract(Long clientUserId, Long applicationId) {
        Application application = applicationService.getApplication(applicationId);
        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        contractValidator.validateContractOwner(clientUserId, jobPosting.clientId());

        Contract contract = contractRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new CustomException(ErrorCode.INVALID_CONTRACT_STATUS);
        }

        return ContractDraftResponse.from(contract);
    }

    public List<ContractListItemResponse> getContracts(
            Long userId,
            String role,
            ContractListStatus status
    ) {
        return contractQueryService.getContracts(userId, role, status);
    }

    public ContractStatusResponse getContractByApplicationId(Long userId, Long applicationId) {
        Application application = applicationService.getApplication(applicationId);
        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        boolean isModel = model.getUser().getId().equals(userId);
        boolean isClient = jobPosting.clientId().equals(userId);

        if (!isModel && !isClient) {
            throw new CustomException(ErrorCode.CONTRACT_ACCESS_DENIED);
        }

        Contract contract = contractRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        return ContractStatusResponse.from(contract, application);
    }

    @Transactional
    public ContractViewResponse viewContract(Long modelUserId, Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new CustomException(ErrorCode.CONTRACT_NOT_FOUND));

        contractValidator.validateViewable(contract);

        Application application = applicationService.getApplication(contract.getApplicationId());

        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        contractValidator.validateContractTargetModel(modelUserId, model.getUser().getId());

        contract.markViewedAt(LocalDateTime.now());

        return ContractViewResponse.from(contract);
    }

    private ContractResponse rewriteDraftableContract(
            Contract existingContract,
            ContractCreateRequest request
    ) {
        if (existingContract.getStatus() != ContractStatus.REJECTED
                && existingContract.getStatus() != ContractStatus.DRAFT) {
            throw new CustomException(ErrorCode.CONTRACT_ALREADY_EXISTS);
        }

        existingContract.rewriteDraft(
                request.contractType(),
                request.shootStartAt(),
                request.shootEndAt(),
                request.location(),
                request.payment(),
                request.payType(),
                request.usageScope(),
                request.memo(),
                request.pdfUrl()
        );

        return ContractResponse.from(existingContract);
    }

    private ContractResponse createNewContract(ContractCreateRequest request) {
        Contract contract = Contract.createDraft(
                request.applicationId(),
                request.contractType(),
                request.shootStartAt(),
                request.shootEndAt(),
                request.location(),
                request.payment(),
                request.payType(),
                request.usageScope(),
                request.memo(),
                request.pdfUrl()
        );

        try {
            return ContractResponse.from(contractRepository.save(contract));
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.CONTRACT_ALREADY_EXISTS);
        }
    }

    public List<ContractTemplateResponse> getTemplates() {
        return contractTemplateRepository.findAll().stream()
                .map(ContractTemplateResponse::from)
                .toList();
    }

    private void updateJobPostingAfterAgreement(Application application) {
        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        if (jobPosting.requiredCount() == null) {
            jobPostingService.markShooting(application.getJobPostingId());
            return;
        }

        long confirmedCount = applicationRepository.countByJobPostingIdAndStatusIn(
                application.getJobPostingId(),
                List.of(
                        ApplicationStatus.SHOOTING,
                        ApplicationStatus.COMPLETED
                )
        );

        if (confirmedCount >= jobPosting.requiredCount()) {
            jobPostingService.markShooting(application.getJobPostingId());
        }
    }

    private void confirmContract(Contract contract, Application application) {
        JobPostingResponse jobPosting = jobPostingService.getJobPosting(application.getJobPostingId());

        Model model = modelRepository.findById(application.getModelId())
                .orElseThrow(() -> new CustomException(ErrorCode.MODEL_NOT_FOUND));

        User clientUser = userService.findById(jobPosting.clientId());
        User modelUser = userService.findById(model.getUser().getId());

        String signedPdfUrl = contractDocumentService.generateSignedPdf(
                contract,
                jobPosting,
                clientUser,
                model,
                modelUser
        );

        contract.confirm(signedPdfUrl, LocalDateTime.now());
        application.shoot();
        updateJobPostingAfterAgreement(application);

        contractNotificationService.sendContractConfirmedNotifications(contract, application);
    }
}
