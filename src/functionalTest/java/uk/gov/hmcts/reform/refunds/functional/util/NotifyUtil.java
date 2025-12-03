package uk.gov.hmcts.reform.refunds.functional.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.refunds.functional.config.IdamService;
import uk.gov.hmcts.reform.refunds.functional.config.TestConfigProperties;
import uk.gov.service.notify.Notification;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.NotificationList;

import java.util.Optional;

@Component
public final class NotifyUtil {
    private static final int MAX_PAGES = 3;
    private static final Logger LOG = LoggerFactory.getLogger(IdamService.class);
    private TestConfigProperties testConfig;

    @Autowired
    public NotifyUtil(TestConfigProperties testConfig) {
        this.testConfig = testConfig;
    }

    public String getNotifyEmailBody(Notification notification) {
        if (notification != null) {
            return notification.getBody();
        }
        return null;
    }

    public String getNotifyEmailSubject(Notification notification) {
        if (notification != null) {
            Optional<String> emailSubject = notification.getSubject();
            if (emailSubject.isPresent()) {
                return emailSubject.get();
            }
        }
        return null;
    }

    public Notification findEmailNotificationFromNotifyClient(String emailAddress) throws NotificationClientException {
        NotificationClient notificationClient = new NotificationClient(testConfig.getNotificationApiKey());
        NotificationList notificationList = notificationClient.getNotifications(null, "email", null, null);
        Notification emailNotification = getEmailNotificationFromNotifyList(notificationList, emailAddress);

        int page = 0;
        while (page < MAX_PAGES && emailNotification == null && notificationList.getNextPageLink().isPresent()) {
            Optional<String> nextPageLink = notificationList.getNextPageLink();
            LOG.info("Searching notify emails, next page {}", nextPageLink.get());
            MultiValueMap<String, String> parameters = UriComponentsBuilder
                .fromUriString(nextPageLink.get())
                .build().getQueryParams();
            String olderThanId = parameters.getFirst("older_than");
            notificationList = notificationClient.getNotifications(null, "email", null, olderThanId);
            emailNotification = getEmailNotificationFromNotifyList(notificationList, emailAddress);
            page++;
        }
        return emailNotification;
    }

    private Notification getEmailNotificationFromNotifyList(NotificationList notificationList, String emailAddress) {
        for (Notification notification : notificationList.getNotifications()) {
            if (notification.getEmailAddress().isPresent() && notification.getEmailAddress().get().equals(emailAddress)) {
                LOG.info("Found Notification for email: " + emailAddress);
                return notification;
            }
        }
        return null;
    }
}
