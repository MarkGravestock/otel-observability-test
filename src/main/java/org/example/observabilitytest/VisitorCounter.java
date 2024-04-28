package org.example.observabilitytest;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VisitorCounter {
  @Id
  @GeneratedValue(strategy= GenerationType.AUTO)
  private Long id;

  private Long visitorCount;

  public static VisitorCounter of(Long id) {
    return new VisitorCounter(id, 0L);
  }

  public void increment() {
    visitorCount++;
  }
}
