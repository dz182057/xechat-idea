package cn.xeblog.commons.entity.pet;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PetProfileDTO {
    private String accountId;
    private Assets assets = new Assets();
    private List<Object> items = new ArrayList<>();
    private List<Object> collections = new ArrayList<>();
    private List<Dog> dogs = new ArrayList<>();
    private String companionDogId;
    private CheckinStatus checkinStatus = new CheckinStatus();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Assets {
        private int bones = 100;
        private int food = 0;
        private int makeupCards = 0;
        private int dogSlots = 1;
        private int energyLimit = 10;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dog {
        private String id;
        private String name;
        private String breed;
        private String stage;
        private int speed;
        private int stamina;
        private int burst;
        private int wisdom;
        private int bond;
        private int energy;
        private String status;
        private int raceCount;
        private int raceFirstCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckinStatus {
        private String serverDate;
        private boolean todayCheckedIn;
        private int cycleDay;
        private List<String> checkedDatesInMonth = new ArrayList<>();
    }
}
