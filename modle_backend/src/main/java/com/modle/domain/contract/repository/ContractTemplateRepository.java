package com.modle.domain.contract.repository;

import com.modle.domain.contract.entity.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
    // 기본 템플릿은 MVP로 하나만 존재하므로, 가장 최근에 생성된 템플릿을 가져오는 메서드
    Optional<ContractTemplate> findFirstByOrderByIdAsc();
}
