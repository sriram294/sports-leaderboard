package com.org.playboard.repository.sport;

import com.org.playboard.entity.sport.Sport;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SportRepository extends JpaRepository<Sport, Short> {

    Optional<Sport> findByCode(String code);
}
