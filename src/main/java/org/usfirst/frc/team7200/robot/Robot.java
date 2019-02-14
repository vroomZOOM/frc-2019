/*----------------------------------------------------------------------------*/
/* Copyright (c) 2017-2018 FIRST. All Rights Reserved.                        */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/

package org.usfirst.frc.team7200.robot;

import edu.wpi.first.wpilibj.IterativeRobot;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.Talon;
import edu.wpi.first.wpilibj.Spark;
import edu.wpi.first.wpilibj.command.Command;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.CameraServer;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane.RestoreAction;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.buttons.Trigger;
import edu.wpi.first.wpilibj.interfaces.Gyro;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.ADXRS450_Gyro;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.I2C;
import edu.wpi.first.wpilibj.I2C.Port;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.Servo;
import edu.wpi.first.wpilibj.Ultrasonic;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANEncoder;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTableEntry;

public class Robot extends IterativeRobot {

	Command autoCommand;
	double rpi_Turn;
	NetworkTableEntry rpi_TurnE;
	/****************************************************************************************************/
	DigitalInput b_ballIn = new DigitalInput(2);// check for ball in bucket
	DigitalInput b_diskOn = new DigitalInput(3);
	DigitalInput limitUp = new DigitalInput(4);
	DigitalInput limitDown = new DigitalInput(5);
	/****************************************************************************************************/
	SendableChooser<Integer> teamStatus;
	SendableChooser<Integer> autoPlay;
	/****************************************************************************************************/
	protected DifferentialDrive m_myRobot; // basic driving variables
	protected Joystick driverstick = new Joystick(0);
	protected Joystick techstick = new Joystick(1);
	/****************************************************************************************************/
	protected Compressor p = new Compressor(0); // pneumatics control variables
	Solenoid p_shootSolenoid = new Solenoid(0);
	Solenoid p_retractSolenoid = new Solenoid(1);
	Solenoid p_Deploy = new Solenoid(2);
	Solenoid p_unDeploy = new Solenoid(3);
	/****************************************************************************************************/
	double stickReverse;// multiply by this when opposite control is needed on demand
	/****************************************************************************************************/
	private static I2C Wire = new I2C(Port.kOnboard, 1);// slave I2C device address 1 (rio is master)
	private static I2C Wire1 = new I2C(Port.kOnboard, 2);
	byte[] i2cbuffer = new byte[8];
	/****************************************************************************************************/
	boolean auto; // auto run variables
	double turnSpeed;
	Ultrasonic s_sensor = new Ultrasonic(0, 1);// ping, then echo
	/****************************************************************************************************/
	private static final int liftDeviceID = 0;
	private CANSparkMax m_liftMotor;
	private CANEncoder m_encoder;

	/****************************************************************************************************/

	TalonSRX m_eject = new TalonSRX(1); // elevator control variables
	VictorSPX m_ballIn = new VictorSPX(2);
	TalonSRX m_tilt = new TalonSRX(3);
	boolean ballIn;
	boolean diskOn;
	boolean liftenable;
	boolean lifttopMax;
	boolean liftdownMin;
	boolean levelQue;
	boolean downRequest;
	boolean levelReq;
	boolean level2Req;
	boolean level3Req;
	Timer popTime = new Timer();

	/****************************************************************************************************/

	@Override
	public void robotInit() {
		NetworkTableInstance inst = NetworkTableInstance.getDefault();
		NetworkTable table = inst.getTable("datatable");
		rpi_TurnE = table.getEntry("angle");

		Spark m_left0 = new Spark(0); // motors are plugged into ports 0,1,2,3 into the roborio
		Spark m_left1 = new Spark(1);
		SpeedControllerGroup m_left = new SpeedControllerGroup(m_left0, m_left1);// motor 0 and 1 are the left side
																					// motors

		Spark m_right2 = new Spark(2);
		Spark m_right3 = new Spark(3);
		SpeedControllerGroup m_right = new SpeedControllerGroup(m_right2, m_right3);// motor 2 and 3 are the right side
																					// motors

		m_myRobot = new DifferentialDrive(m_left, m_right); // new differential drive - another can be created for
															// another set of wheels

		p.setClosedLoopControl(true);// start the compressor

		m_myRobot.arcadeDrive(0, 0);// set drivetrain to 0 movement
		boolean auto = false;
		turnSpeed = 0;
		s_sensor.setAutomaticMode(true);

		m_liftMotor = new CANSparkMax(liftDeviceID, MotorType.kBrushless);
		m_encoder = m_liftMotor.getEncoder();
		m_liftMotor.set(0);
		m_ballIn.set(ControlMode.PercentOutput, 0);
		m_eject.set(ControlMode.PercentOutput, 0);
		levelQue = false;
		levelReq = false;
		level2Req = false;
		level3Req = false;
		downRequest = false;

	}

	@Override
	public void autonomousInit() {
	}

	@Override
	public void autonomousPeriodic() {
		NetworkTableInstance inst = NetworkTableInstance.getDefault();
		NetworkTable table = inst.getTable("datatable");
		rpi_TurnE = table.getEntry("angle");
		rpi_Turn = rpi_TurnE.getDouble(0);
		System.out.println(rpi_Turn);


		// auto ball pick up - this needs tweaking

		//if(rpi_Turn)

			double driveAngle = (rpi_Turn - 192) / 100;

			m_myRobot.arcadeDrive(0.6, turnSpeed);

			turnSpeed = driveAngle;

			if (turnSpeed > 0.6) {
				turnSpeed = 0.6;
			}

			if (turnSpeed < -0.6) {
				turnSpeed = -0.6;
		
		}
	}

	@Override
	public void teleopInit() {
		CameraServer.getInstance().startAutomaticCapture();// start the camera

		turnSpeed = 0;

	}

	@Override
	public void teleopPeriodic() {

		// rpi_TurnE.setDouble(rpi_Turn);
		// notes - configure spark max and talon and victor spx CANOPEN addresses

		boolean normalDrive = driverstick.getRawButton(10); // declaring what the joystick buttons are
		boolean revDrive = driverstick.getRawButton(12);
		double robotSpeed = (driverstick.getThrottle() - 1.0) / -2;
		boolean autoRun = driverstick.getRawButton(2);

		boolean level1 = driverstick.getRawButton(3);
		boolean level1d = techstick.getRawButton(3);
		boolean level2 = driverstick.getRawButton(4);
		boolean level2d = techstick.getRawButton(4);
		boolean level3 = driverstick.getRawButton(6);
		boolean level3d = techstick.getRawButton(6);
		boolean down = driverstick.getRawButton(5);
		boolean downd = techstick.getRawButton(5);

		boolean autoLockout = driverstick.getTrigger();

		if ((level1 || level1d) && !levelQue) {
			levelQue = true;
			levelReq = !levelReq;
		}
		if ((level2 || level2d) && !levelQue) {
			levelQue = true;
			level2Req = !level2Req;
		}
		if ((level3 || level3d) && !levelQue) {
			levelQue = true;
			level3Req = !level3Req;
		}
		if ((down || downd) && !levelQue) {
			levelQue = true;
			downRequest = !downRequest;
		}
		if (!level1 && !level1d && !level2 && !level2d && !level3 && !level3d && !down && downd) {
			levelQue = false;
		}

		System.out.println(driverstick.getPOV());

		double range = s_sensor.getRangeInches();

		Wire.read(1, 1, i2cbuffer);

		m_liftMotor.set(0);

		m_myRobot.arcadeDrive(driverstick.getY() * robotSpeed * stickReverse, driverstick.getX() * robotSpeed);

		/****************************************************************************************************/
		// checking the switches
		if (b_ballIn.get()) {

			ballIn = false;// AKA no object in bucket

		} else {

			ballIn = true;
		}

		if (b_diskOn.get()) {

			diskOn = false;
		}

		else {

			diskOn = true;
		}

		if (limitUp.get()) {

			lifttopMax = true;

		}

		else {

			lifttopMax = false;
		}

		if (limitDown.get()) {

			liftdownMin = true;
		}

		else {

			liftdownMin = false;
		}

		/****************************************************************************************************/
		if (autoRun && !ballIn) { // auto ball pick up - this needs tweaking

			liftenable = true;

			Wire.read(1, 1, i2cbuffer);

			double servoangle = Math.abs(i2cbuffer[0]);
			double driveAngle = (servoangle - 90) / 30;

			m_myRobot.arcadeDrive(0.6, turnSpeed);

			turnSpeed = driveAngle;

			if (turnSpeed > 0.6) {
				turnSpeed = 0.6;
			}

			if (turnSpeed < -0.6) {
				turnSpeed = -0.6;
			}
			if (!ballIn) {

				if (range <= 32) {
					m_myRobot.arcadeDrive(0.6, turnSpeed);

					m_ballIn.set(ControlMode.PercentOutput, -0.8);
					m_eject.set(ControlMode.PercentOutput, 0.2);
				}

				else {

					m_myRobot.arcadeDrive(0.6, turnSpeed);

					m_ballIn.set(ControlMode.PercentOutput, 0);
					m_eject.set(ControlMode.PercentOutput, 0);
				}

			}
		}

		/****************************************************************************************************/
		if (driverstick.getPOV() != -1) {

			if (driverstick.getPOV() == 180) {
				m_ballIn.set(ControlMode.PercentOutput, -0.8);
			}

			if (driverstick.getPOV() == 0 && diskOn) {
				p_shootSolenoid.set(true);
				p_retractSolenoid.set(false);
			}
			if (driverstick.getPOV() == 0 && ballIn){

			}

		} else {
			p_shootSolenoid.set(false);
			p_retractSolenoid.set(true);

			m_ballIn.set(ControlMode.PercentOutput, 0);
			m_eject.set(ControlMode.PercentOutput, 0);
		}
		/****************************************************************************************************/

		if (revDrive) {

			stickReverse = -1.0;
			m_myRobot.arcadeDrive((driverstick.getY() * -1) * 0.7 * stickReverse, (driverstick.getX()) * 0.7);

		}

		if (normalDrive) {

			stickReverse = 1;
			m_myRobot.arcadeDrive((driverstick.getY() * -1) * 0.7 * stickReverse, (driverstick.getX()) * 0.7);

		}

		/****************************************************************************************************/
		if (level1 && autoLockout) {
			m_liftMotor.set(1.0);
		}

		if (level2 && autoLockout) {
			m_liftMotor.set(-0.5);
		}
		if (autoLockout && !level1 && level2) {
			m_liftMotor.set(0);
		}
		/****************************************************************************************************/

		
		/****************************************************************************************************/
		if (ballIn && !lifttopMax && !liftdownMin && !autoLockout) {

			if (level1 && m_encoder.getPosition() <= 250) {

				double liftspeed = (m_encoder.getPosition() - 250) / -20;

				if (liftspeed >= 1.0) {
					liftspeed = 1.0;
				}

				m_liftMotor.set(liftspeed);

			}
			if (level1 == true && m_encoder.getPosition() >= 250) {

				m_liftMotor.set(0);
			}

			if (level2 == true && m_encoder.getPosition() <= 500) {

				double liftspeed = (m_encoder.getPosition() - 500) / -20;

				if (liftspeed >= 1.0) {
					liftspeed = 1.0;
				}

				m_liftMotor.set(liftspeed);

			}

			if (level2 == true && m_encoder.getPosition() >= 500) {
				m_liftMotor.set(0);
			}

			if (level1 == false && level2 == false && level3 == false && down == false) {
				m_liftMotor.set(0);
			}

			if (down && m_encoder.getPosition() >= 50) {

				double liftspeed = (m_encoder.getPosition()) / -20;

				if (liftspeed <= -0.6) {
					liftspeed = -0.6;
				}

				m_liftMotor.set(liftspeed);

			}
			if (down == true && m_encoder.getPosition() <= 50) {
				m_liftMotor.set(0);
			}
		}

		if (diskOn && !lifttopMax && !liftdownMin && !autoLockout) {

			if (level1 == true && m_encoder.getPosition() <= 100) {

				double liftspeed = (m_encoder.getPosition() - 100) / -10;

				if (liftspeed >= 0.7) {
					liftspeed = 0.7;
				}

				m_liftMotor.set(liftspeed);

			}
			if (level1 == true && m_encoder.getPosition() >= 100) {

				m_liftMotor.set(0);
			}

			if (level2 == true && m_encoder.getPosition() <= 200) {

				double liftspeed = (m_encoder.getPosition() - 200) / -10;

				if (liftspeed >= 1.0) {
					liftspeed = 1.0;
				}

				m_liftMotor.set(liftspeed);

			}

			if (level2 == true && m_encoder.getPosition() >= 200) {
				m_liftMotor.set(0);
			}

			if (level1 == false && level2 == false && level3 == false && down == false) {
				m_liftMotor.set(0);
			}

			if (down && m_encoder.getPosition() >= 50) {

				double liftspeed = (m_encoder.getPosition()) / -10;

				if (liftspeed <= -0.6) {
					liftspeed = -0.6;
				}

				m_liftMotor.set(liftspeed);

			}
			if (down == true && m_encoder.getPosition() <= 50) {
				m_liftMotor.set(0);
			}
		}
		/****************************************************************************************************/

	}

	@Override
	public void testPeriodic() {
	}
}
