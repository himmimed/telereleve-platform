package ma.ceac.telereleve.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlertStatusTest {

    @Test
    void allowsTakingChargeResolvingAndReopening() {
        assertThat(AlertStatus.NEW.canChangeTo(AlertStatus.IN_PROGRESS)).isTrue();
        assertThat(AlertStatus.NEW.canChangeTo(AlertStatus.RESOLVED)).isTrue();
        assertThat(AlertStatus.IN_PROGRESS.canChangeTo(AlertStatus.RESOLVED)).isTrue();
        assertThat(AlertStatus.RESOLVED.canChangeTo(AlertStatus.NEW)).isTrue();
    }

    @Test
    void refusesMeaninglessChanges() {
        assertThat(AlertStatus.IN_PROGRESS.canChangeTo(AlertStatus.NEW)).isFalse();
        assertThat(AlertStatus.RESOLVED.canChangeTo(AlertStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    void onlyHighAndCriticalAlertsAreMailed() {
        assertThat(Severity.CRITICAL.notifiedByMail()).isTrue();
        assertThat(Severity.HIGH.notifiedByMail()).isTrue();
        assertThat(Severity.MEDIUM.notifiedByMail()).isFalse();
    }

    @Test
    void openMeansNewOrInProgress() {
        assertThat(AlertStatus.NEW.isOpen()).isTrue();
        assertThat(AlertStatus.IN_PROGRESS.isOpen()).isTrue();
        assertThat(AlertStatus.RESOLVED.isOpen()).isFalse();
    }
}
