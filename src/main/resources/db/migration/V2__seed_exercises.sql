-- Database Migration V2: Seed Default Calisthenics Exercises

INSERT INTO exercises (name, code, category, difficulty, description, target_muscles, instructions)
VALUES
('Tuck Planche', 'TUCK_PLANCHE', 'SKILL', 'INTERMEDIATE', 
 'Beginner planche progression with knees tucked into chest.', 
 'Shoulders, Scapula, Triceps, Core', 
 'Keep arms fully locked out (>170°), protract scapula, and lean forward over wrists.'),

('Advanced Tuck Planche', 'ADV_TUCK_PLANCHE', 'SKILL', 'ADVANCED', 
 'Planche progression with flat back and knees un-tucked to 90 degrees.', 
 'Anterior Deltoids, Lower Back, Core', 
 'Maintain scapular protraction and elbow lock while extending hips backward.'),

('Straddle Planche', 'STRADDLE_PLANCHE', 'SKILL', 'ADVANCED', 
 'Planche with legs extended outward in a wide straddle.', 
 'Anterior Deltoids, Glutes, Core, Wrist Extensors', 
 'Straight body line from shoulders to hips, straddle legs to lower center of mass.'),

('Full Planche', 'FULL_PLANCHE', 'SKILL', 'ELITE', 
 'Ultimate calisthenics push isometric hold with horizontal straight body.', 
 'Anterior Deltoids, Core, Lower Back, Forearms', 
 'Locked elbows, max forward lean, hollow body shape parallel to ground.'),

('Handstand', 'HANDSTAND', 'SKILL', 'INTERMEDIATE', 
 'Vertical body inversion balance on hands.', 
 'Shoulders, Core, Forearms, Traps', 
 'Push tall through shoulders, open shoulder angle (180°), keep hollow body line.'),

('Muscle-Up', 'MUSCLE_UP', 'PULL', 'ADVANCED', 
 'Combination of explosive pull-up transitioning over the bar into a dip.', 
 'Lats, Explosive Pulling, Triceps, Chest', 
 'Drive elbows up and over bar at peak height, roll wrists over top.'),

('Front Lever', 'FRONT_LEVER', 'SKILL', 'ADVANCED', 
 'Horizontal body hold hanging from pull-up bar facing ceiling.', 
 'Lats, Rear Deltoids, Core, Grips', 
 'Depress and retract scapula, straight arm lat pull down force.'),

('Parallel Bar Dips', 'DIPS', 'PUSH', 'BEGINNER', 
 'Bodyweight pushing exercise targeting chest and triceps.', 
 'Triceps, Lower Chest, Anterior Deltoids', 
 'Lower body until upper arm is parallel to bars, push to full lockout.');
