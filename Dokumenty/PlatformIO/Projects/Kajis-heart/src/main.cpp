#include <Arduino.h>

// put function declarations here:
constexpr uint8_t output = 18;
struct PulseStep {
  uint8_t brightness;
  uint16_t duration;
};


bool ok;
uint8_t duty = 0;

void setup() {
  ok = ledcAttach(output, 5000, 8);
}

void loop() {
  delay(5);
  duty = (duty + 1) % 256;
  ledcWrite(output, duty);
}


