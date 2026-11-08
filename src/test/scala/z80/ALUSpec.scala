package z80

import chisel3._
import chiseltest._
import org.scalatest.FlatSpec

class ALUSpec extends FlatSpec with ChiselScalatestTester {
  behavior of "ALU"

  private val arithmeticOperations = Seq(
    (0x80, false, false),
    (0x88, false, true),
    (0x90, true, false),
    (0x98, true, true)
  )

  private def arithmeticResult(a: Int, b: Int, carry: Int, subtract: Boolean): Int = {
    val raw = if (subtract) a - b - carry else a + b + carry
    raw & 0xFF
  }

  private def arithmeticFlags(a: Int, b: Int, carry: Int, subtract: Boolean): Int = {
    val result = arithmeticResult(a, b, carry, subtract)
    val overflow =
      if (subtract) ((a ^ b) & (a ^ result) & 0x80) != 0
      else ((~(a ^ b)) & (a ^ result) & 0x80) != 0
    val halfCarry =
      if (subtract) (a & 0x0F) < ((b & 0x0F) + carry)
      else (a & 0x0F) + (b & 0x0F) + carry > 0x0F
    val carryFlag =
      if (subtract) a - b - carry < 0
      else a + b + carry > 0xFF

    (result & 0xA8) |
      (if (result == 0) 0x40 else 0) |
      (if (halfCarry) 0x10 else 0) |
      (if (overflow) 0x04 else 0) |
      (if (subtract) 0x02 else 0) |
      (if (carryFlag) 0x01 else 0)
  }

  private def logicalFlags(result: Int): Int = {
    (result & 0xA8) |
      (if (result == 0) 0x40 else 0) |
      0x10 |
      (if (Integer.bitCount(result) % 2 == 0) 0x04 else 0)
  }

  private def compareFlags(a: Int, b: Int): Int = {
    val result = (a - b) & 0xFF
    val overflow = ((a ^ b) & (a ^ result) & 0x80) != 0
    val halfBorrow = (a & 0x0F) < (b & 0x0F)

    (result & 0x80) |
      (if (result == 0) 0x40 else 0) |
      (b & 0x28) |
      (if (halfBorrow) 0x10 else 0) |
      (if (overflow) 0x04 else 0) |
      0x02 |
      (if (a < b) 0x01 else 0)
  }

  it should "perform arithmetic operations for every operand and carry value" in {
    test(new ALU) { c =>
      for {
        (calcType, subtract, usesCarry) <- arithmeticOperations
        a <- 0 until 256
        b <- 0 until 256
        carryIn <- 0 to 1
      } {
        val carry = if (usesCarry) carryIn else 0
        val result = arithmeticResult(a, b, carry, subtract)

        c.io.input_A.poke(a.U(8.W))
        c.io.input_B.poke(b.U(8.W))
        c.io.input_carry.poke(carryIn.U(1.W))
        c.io.calc_type.poke(calcType.U(8.W))
        c.io.output_C.expect(result.U(8.W))
        c.io.flag.expect(arithmeticFlags(a, b, carry, subtract).U(8.W))
      }
    }
  }

  it should "perform logical operations with correct flags for every operand" in {
    test(new ALU) { c =>
      for {
        calcType <- Seq(0xA0, 0xA8, 0xB0)
        a <- 0 until 256
        b <- 0 until 256
        carryIn <- 0 to 1
      } {
        val result = calcType match {
          case 0xA0 => a & b
          case 0xA8 => a ^ b
          case 0xB0 => a | b
        }

        c.io.input_A.poke(a.U(8.W))
        c.io.input_B.poke(b.U(8.W))
        c.io.input_carry.poke(carryIn.U(1.W))
        c.io.calc_type.poke(calcType.U(8.W))
        c.io.output_C.expect(result.U(8.W))
        c.io.flag.expect(logicalFlags(result).U(8.W))
      }
    }
  }

  it should "preserve A and set all flags for every CP operand and carry value" in {
    test(new ALU) { c =>
      c.io.calc_type.poke(0xB8.U(8.W))

      for {
        a <- 0 until 256
        b <- 0 until 256
        carryIn <- 0 to 1
      } {
        c.io.input_A.poke(a.U(8.W))
        c.io.input_B.poke(b.U(8.W))
        c.io.input_carry.poke(carryIn.U(1.W))
        c.io.output_C.expect(a.U(8.W))
        c.io.flag.expect(compareFlags(a, b).U(8.W))
      }
    }
  }

  behavior of "ALU16"

  it should "sign-extend offsets and wrap addresses at 16 bits" in {
    test(new ALU16) { c =>
      val registers = Seq(0x0000, 0x0001, 0x007F, 0x0080, 0x7F80, 0x7FFF, 0x8000, 0xFF00, 0xFF7F, 0xFF80, 0xFFFF)

      for (register <- registers; offset <- -128 to 127) {
        c.io.input_register.poke(register.U(16.W))
        c.io.offset.poke(offset.S(8.W))
        c.io.output.expect(((register + offset) & 0xFFFF).U(16.W))
      }
    }
  }
}
