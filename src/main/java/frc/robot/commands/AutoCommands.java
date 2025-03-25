package frc.robot.commands;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.DriverStation.Alliance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.Constants.FieldConstants;
import frc.robot.subsystems.AlgaArm;
import frc.robot.subsystems.CoralArm;
import frc.robot.subsystems.CoralManipulator;
import frc.robot.subsystems.Swerve;

/**
 * Container for autonomous command sequences used during the autonomous period.
 * This class provides factory methods that create and return command sequences
 * for different autonomous routines. Each method constructs a complete autonomous
 * strategy that can be selected and run during the autonomous phase.
 */
public class AutoCommands {
    private AutoCommands() {
        // Utility class - prevent instantiation since all methods are static
    }

    /**
     * Creates a simple test command that drives the robot backward at a fixed speed for a set time.
     * Useful for testing basic autonomous movement and verifying drivetrain functionality.
     * 
     * @return A command that drives backward at 1.0 m/s for 3 seconds and then stops
     */
    public static Command simpleBackwardDrive() {
        Swerve swerve = Swerve.getInstance();

        return Commands.run(() -> {
            // Drive backward at 1.0 m/s in the negative x direction (robot-relative)
            swerve.getSwerveDrive().setChassisSpeeds(
                    new ChassisSpeeds(-1.0, 0, 0) // x velocity, y velocity, rotational velocity
            );
        }, swerve)
                .withTimeout(3.0) // Run for exactly 3 seconds
                .andThen(() -> {
                    // Stop all robot movement by setting velocities to zero
                    swerve.getSwerveDrive().setChassisSpeeds(
                            new ChassisSpeeds(0, 0, 0));
                });
    }

    /**
     * Autonomous routine that starts from the left spawn position, delivers coral to a reef location,
     * acquires more coral from the left intake station, and delivers it to another reef location.
     * 
     * The sequence:
     * 1. Drives to first reef position while positioning arms
     * 2. Drops coral at first position (i)
     * 3. Drives to intake station to collect new coral
     * 4. Delivers collected coral to second reef position (j)
     * 
     * @return A command sequence for the complete left-side autonomous routine
     */
    public static Command leftToReef() {
        // Get subsystem instances
        Swerve swerve = Swerve.getInstance();
        CoralArm coralArm = CoralArm.getInstance();
        AlgaArm algaArm = AlgaArm.getInstance();
        CoralManipulator coralManipulator = CoralManipulator.getInstance();

        return Commands.sequence(
                // Phase 1: Navigate to first reef position while preparing arms
                Commands.parallel(
                        Commands.runOnce(() -> {
                            Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
                            Pose2d firstPose = FieldConstants.getPoiByTagAndAddress("CORAL_REEF", "i").get(alliance);
                            swerve.driveToPose(firstPose).schedule();
                        }),
                        coralArm.setMid(), // Position coral arm for delivery
                        algaArm.release()), // Ensure alga arm is not in the way
                
                // Phase 2: Wait for movement to complete before delivery
                Commands.waitUntil(() -> {
                    ChassisSpeeds speeds = swerve.getRobotVelocity();
                    return Math.abs(speeds.vxMetersPerSecond) < 0.1 && Math.abs(speeds.vyMetersPerSecond) < 0.1;
                }),
                Commands.waitSeconds(2.0), // Stabilization period to ensure proper positioning
                
                // Phase 3: Release coral at the first position
                coralManipulator.drop().withTimeout(1.5),
                
                // Phase 4: Navigate to intake station while preparing for intake
                Commands.parallel(
                        Commands.runOnce(() -> {
                            Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
                            Pose2d intakePose = FieldConstants.getPoiByTagAndAddress("INTAKE_STATION", "left").get(alliance);
                            swerve.driveToPose(intakePose).schedule();
                        }),
                        Commands.waitSeconds(1.0).andThen(coralArm.setIntake()), // Position for intake after a delay
                        coralManipulator.intake()), // Activate intake mechanism
                
                // Phase 5: Return to second reef position for delivery
                Commands.parallel(
                        Commands.runOnce(() -> {
                            Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
                            Pose2d secondPose = FieldConstants.getPoiByTagAndAddress("CORAL_REEF", "j").get(alliance);
                            swerve.driveToPose(secondPose).schedule();
                        }),
                        coralArm.setMid(), // Position for delivery
                        algaArm.release()),
                
                // Phase 6: Wait for movement to complete before second delivery
                Commands.waitUntil(() -> {
                    ChassisSpeeds speeds = swerve.getRobotVelocity();
                    return Math.abs(speeds.vxMetersPerSecond) < 0.1 && Math.abs(speeds.vyMetersPerSecond) < 0.1;
                }),
                Commands.waitSeconds(2.0), // Stabilization period
                
                // Phase 7: Release coral at the second position
                coralManipulator.drop().withTimeout(1.5)).withName("leftToReef Auto");
    }

    /**
     * Autonomous routine that starts from the right spawn position, delivers coral to a reef location,
     * acquires more coral from the right intake station, and delivers it to another reef location.
     * 
     * The sequence:
     * 1. Drives to first reef position while positioning arms
     * 2. Drops coral at first position (f)
     * 3. Drives to intake station to collect new coral
     * 4. Delivers collected coral to second reef position (e)
     * 
     * @return A command sequence for the complete right-side autonomous routine
     */
    public static Command rightToReef() {
        // Get subsystem instances
        Swerve swerve = Swerve.getInstance();
        CoralArm coralArm = CoralArm.getInstance();
        AlgaArm algaArm = AlgaArm.getInstance();
        CoralManipulator coralManipulator = CoralManipulator.getInstance();

        return Commands.sequence(
                // Phase 1: Navigate to first reef position while preparing arms
                Commands.parallel(
                        Commands.runOnce(() -> {
                            Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
                            Pose2d firstPose = FieldConstants.getPoiByTagAndAddress("CORAL_REEF", "f").get(alliance);
                            swerve.driveToPose(firstPose).schedule();
                        }),
                        coralArm.setMid(), // Position coral arm for delivery
                        algaArm.release()), // Ensure alga arm is not in the way
                
                // Phase 2: Wait for movement to complete before delivery
                Commands.waitUntil(() -> {
                    ChassisSpeeds speeds = swerve.getRobotVelocity();
                    return Math.abs(speeds.vxMetersPerSecond) < 0.1 && Math.abs(speeds.vyMetersPerSecond) < 0.1;
                }),
                Commands.waitSeconds(2.0), // Stabilization period to ensure proper positioning
                
                // Phase 3: Release coral at the first position
                coralManipulator.drop().withTimeout(1.5),
                
                // Phase 4: Navigate to intake station while preparing for intake
                Commands.parallel(
                        Commands.runOnce(() -> {
                            Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
                            Pose2d intakePose = FieldConstants.getPoiByTagAndAddress("INTAKE_STATION", "right").get(alliance);
                            swerve.driveToPose(intakePose).schedule();
                        }),
                        Commands.waitSeconds(1.0).andThen(coralArm.setIntake()), // Position for intake after a delay
                        coralManipulator.intake()), // Activate intake mechanism
                
                // Phase 5: Return to second reef position for delivery
                Commands.parallel(
                        Commands.runOnce(() -> {
                            Alliance alliance = DriverStation.getAlliance().orElse(Alliance.Blue);
                            Pose2d secondPose = FieldConstants.getPoiByTagAndAddress("CORAL_REEF", "e").get(alliance);
                            swerve.driveToPose(secondPose).schedule();
                        }),
                        coralArm.setMid(), // Position for delivery
                        algaArm.release()),
                
                // Phase 6: Wait for movement to complete before second delivery
                Commands.waitUntil(() -> {
                    ChassisSpeeds speeds = swerve.getRobotVelocity();
                    return Math.abs(speeds.vxMetersPerSecond) < 0.1 && Math.abs(speeds.vyMetersPerSecond) < 0.1;
                }),
                Commands.waitSeconds(2.0), // Stabilization period
                
                // Phase 7: Release coral at the second position
                coralManipulator.drop().withTimeout(1.5)).withName("rightToReef Auto");
    }
}
