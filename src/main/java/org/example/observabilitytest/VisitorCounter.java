package org.example.observabilitytest;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Data
@NoArgsConstructor
public class VisitorCounter implements Persistable<Long> {
  @Id
  private Long id;

  private Long visitorCount;

  @Transient
  private boolean newEntity;

  @Override
  public boolean isNew() {
    return newEntity;
  }

  public static VisitorCounter of(Long id) {
    var vc = new VisitorCounter();
    vc.id = id;
    vc.visitorCount = 0L;
    vc.newEntity = true;
    return vc;
  }

  public void increment() {
    visitorCount++;
  }
}