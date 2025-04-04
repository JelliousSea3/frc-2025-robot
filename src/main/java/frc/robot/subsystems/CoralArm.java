package frc.robot.subsystems;

import static edu.wpi.first.units.Units.Degree;
import static edu.wpi.first.units.Units.Inch;

import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.units.measure.Angle;
import edu.wpi.first.units.measure.Distance;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import edu.wpi.first.wpilibj2.command.button.Trigger;
import frc.robot.Constants.CoralArmConstants;
import frc.robot.Constants.CoralArmConstants.ArmState;
import frc.robot.helpers.LimitedPID;
import java.util.function.BooleanSupplier;

/** Subsystem that controls the Coral Arm, responsible for moving the arm to different positions. */
public class CoralArm extends SubsystemBase {
  private static CoralArm instance;

  /**
   * Gets the singleton instance of the CoralArm subsystem.
   *
   * @return The singleton instance.
   */
  public static CoralArm getInstance() {
    if (instance == null) {
      instance = new CoralArm();
    }
    return instance;
  }

  private LimitedPID elevatorController;
  private LimitedPID elbowController;
  private NetworkTable table;

  private CoralArm() {
    elevatorController =
        new LimitedPID(
            CoralArmConstants.ELEVATOR_CAN_ID,
            CoralArmConstants.ELEVATOR_DISTANCE_PER_ROTATION.in(Inch),
            CoralArmConstants.ELEVATOR_MIN_POSITION.in(Inch),
            CoralArmConstants.ELEVATOR_MAX_POSITION.in(Inch),
            CoralArmConstants.ELEVATOR_PID,
            CoralArmConstants.ELEVATOR_BOTTOM_LIMIT_CHANNEL,
            CoralArmConstants.ELEVATOR_TOP_LIMIT_CHANNEL,
            CoralArmConstants.POSITION_TOLERANCE,
            CoralArmConstants.ELEVATOR_INVERTED,
            CoralArmConstants.ELEVATOR_RAMP_RATE,
            CoralArmConstants.ELEVATOR_CURRENT_LIMIT);
    elbowController =
        new LimitedPID(
            CoralArmConstants.ELBOW_CAN_ID,
            CoralArmConstants.ELBOW_ANGLE_PER_ROTATION.in(Degree),
            CoralArmConstants.ELBOW_BACK_ANGLE.in(Degree),
            CoralArmConstants.ELBOW_FRONT_ANGLE.in(Degree),
            CoralArmConstants.ELBOW_PID,
            CoralArmConstants.ELBOW_BACK_LIMIT_CHANNEL,
            CoralArmConstants.ELBOW_FRONT_LIMIT_CHANNEL,
            CoralArmConstants.POSITION_TOLERANCE,
            CoralArmConstants.ELBOW_INVERTED,
            CoralArmConstants.ELBOW_RAMP_RATE,
            CoralArmConstants.ELBOW_CURRENT_LIMIT);
    table = NetworkTableInstance.getDefault().getTable("Robot").getSubTable("CoralArm");
  }

  /** Trigger that is active when the elbow is on the front side. */
  public final Trigger onFront = new Trigger(() -> getCurrentElbowAngle().in(Degree) > 0);

  /** Trigger that is active when the elevator is at a safe height. */
  public final Trigger aboveSafeHeight =
      new Trigger(
          () ->
              getCurrentElevatorHeight().in(Inch)
                  >= CoralArmConstants.SAFE_ELEVATOR_HEIGHT.in(Inch) - 2.5);

  /** Trigger that is active when the elbow angle won't hit the elevator tower */
  public final Trigger inSafeAngle =
      new Trigger(
          () -> {
            double elbowAngle = getCurrentElbowAngle().in(Degree);
            return (elbowAngle < CoralArmConstants.SAFE_ANGLE_DEADZONE_BACK.in(Degree) + 15)
                || (elbowAngle > CoralArmConstants.SAFE_ANGLE_DEADZONE_FRONT.in(Degree) - 15);
          });

  /**
   * Creates a command to set the arm position based on a predefined setpoint
   *
   * @param setpointKey The key of the predefined setpoint in ARM_SETPOINTS
   * @return A command that moves the arm to the specified position
   */
  private Command setPosition(ArmState targetState) {
    BooleanSupplier isSwitchingSides =
        () -> {
          boolean currentSideIsFront = onFront.getAsBoolean();
          boolean targetSideIsFront = targetState.isFront();
          return currentSideIsFront != targetSideIsFront;
        };

    // Command that directly sets the target positions without any safety measures
    Command directMove =
        Commands.runOnce(
            () -> {
              elbowController.setPosition(targetState.elbowAngle().in(Degree));
              elevatorController.setPosition(targetState.elevatorHeight().in(Inch));
            });

    // Safe transition command with upfront calculation of all waypoints
    Command safeTransition =
        Commands.sequence(
            // PHASE 1: Move to safe height and first intermediate angle simultaneously
            Commands.runOnce(
                () -> {
                  double safeAngle =
                      onFront.getAsBoolean()
                          ? CoralArmConstants.SAFE_ANGLE_DEADZONE_FRONT.in(Degree)
                          : CoralArmConstants.SAFE_ANGLE_DEADZONE_BACK.in(Degree);

                  // Set both positions simultaneously
                  elevatorController.setPosition(CoralArmConstants.SAFE_ELEVATOR_HEIGHT.in(Inch));
                  elbowController.setPosition(safeAngle);
                }),

            // Wait until the elevator reaches the safe height
            Commands.waitUntil(aboveSafeHeight),

            // PHASE 2: Set to the final angle
            Commands.runOnce(
                () -> elbowController.setPosition(targetState.elbowAngle().in(Degree))),

            // Wait until elbow is in safe angle
            Commands.waitUntil(inSafeAngle.negate()),
            Commands.waitUntil(inSafeAngle),

            // PHASE 3: Now we're safe to move to final position
            Commands.runOnce(
                () -> elevatorController.setPosition(targetState.elevatorHeight().in(Inch))));

    // Choose transition strategy based on whether we're switching sides
    return Commands.sequence(
            Commands.either(safeTransition, directMove, isSwitchingSides),
            // Wait until positions are reached
            Commands.waitUntil(
                () -> {
                  double elbowError =
                      Math.abs(
                          getCurrentElbowAngle().in(Degree) - targetState.elbowAngle().in(Degree));
                  double elevatorError =
                      Math.abs(
                          getCurrentElevatorHeight().in(Inch)
                              - targetState.elevatorHeight().in(Inch));

                  boolean atPosition = elbowError < 5.0 && elevatorError < 1.0;

                  return atPosition;
                }))
        .withName("setPosition"); // Removed timeout entirely
  }

  /**
   * Creates a command to set the arm position based on a predefined setpoint name
   *
   * @param setpointKey The key of the predefined setpoint in ARM_SETPOINTS
   * @return A command that moves the arm to the specified position
   */
  public Command setPosition(String setpointKey) {
    ArmState state = CoralArmConstants.ARM_SETPOINTS.get(setpointKey);
    if (state == null) {
      return Commands.none().withName("SetPosition-Invalid");
    }
    return setPosition(state).withName("SetPosition-" + setpointKey);
  }

  /**
   * Command to move the arm to the ZERO position
   *
   * @return Command to set the arm to the ZERO position
   */
  public Command setZero() {
    return setPosition("ZERO");
  }

  /**
   * Command to move the arm to the INTAKE position
   *
   * @return Command to set the arm to the INTAKE position
   */
  public Command setIntake() {
    return setPosition("INTAKE");
  }

  /**
   * Command to move the arm to the LOW position
   *
   * @return Command to set the arm to the LOW position
   */
  public Command setLow() {
    return setPosition("LOW");
  }

  /**
   * Command to move the arm to the MID position
   *
   * @return Command to set the arm to the MID position
   */
  public Command setMid() {
    return setPosition("MID");
  }

  /**
   * Command to move the arm to the HIGH position
   *
   * @return Command to set the arm to the HIGH position
   */
  public Command setHigh() {
    return setPosition("HIGH");
  }

  /**
   * Command to move the arm to the CLIMB position
   *
   * @return Command to set the arm to the CLIMB position
   */
  public Command setClimb() {
    return setPosition("CLIMB");
  }

  /**
   * Gets the current elbow angle
   *
   * @return Current elbow angle
   */
  public Angle getCurrentElbowAngle() {
    return Degree.of(elbowController.getPosition());
  }

  /**
   * Gets the current elevator height
   *
   * @return Current elevator height
   */
  public Distance getCurrentElevatorHeight() {
    return Inch.of(elevatorController.getPosition());
  }

  @Override
  public void periodic() {
    elevatorController.update();
    elbowController.update();

    // Publish current positions
    table.getEntry("elevatorHeight").setDouble(getCurrentElevatorHeight().in(Inch));
    table.getEntry("elbowAngle").setDouble(getCurrentElbowAngle().in(Degree));

    // Publish limit switch states
    table.getEntry("elevatorAtMinLimit").setBoolean(elevatorController.isAtMinLimit());
    table.getEntry("elevatorAtMaxLimit").setBoolean(elevatorController.isAtMaxLimit());
    table.getEntry("elbowAtMinLimit").setBoolean(elbowController.isAtMinLimit());
    table.getEntry("elbowAtMaxLimit").setBoolean(elbowController.isAtMaxLimit());

    // Publish subsystem states
    table
        .getEntry("elevatorStateKnown")
        .setBoolean(elevatorController.getState() == LimitedPID.SubsystemState.KNOWN);
    table
        .getEntry("elbowStateKnown")
        .setBoolean(elbowController.getState() == LimitedPID.SubsystemState.KNOWN);

    // Essential motor outputs for monitoring
    table.getEntry("elbowMotorOutput").setDouble(elbowController.getMotor().get());
    table.getEntry("elevatorMotorOutput").setDouble(elevatorController.getMotor().get());
  }
}
