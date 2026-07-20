package com.org.playboard.repository.notification;

import com.org.playboard.entity.notification.NotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
}
