[![Build](https://github.com/alogic/alogic/workflows/Build/badge.svg)](https://github.com/alogic/alogic/actions?query=workflow%3ABuild)
[![codecov](https://codecov.io/gh/alogic/alogic/branch/4.0.x/graph/badge.svg)](https://app.codecov.io/gh/alogic/alogic)

# Alogic

Alogic is a Medium Level Hardware Description Language for digital logic that
compiles swiftly into standard SystemVerilog for implementation in ASIC or
FPGA.

Traditionally register transfer level (RTL) digital designs are specified using
the Verilog or VHDL Hardware Description Languages (HDLs). These standard HDLs
allow for unlimited flexibility in what digital circuits can be expressed, and
are used throughout the implementation flow as representations of these digital
circuits at various levels of abstraction (e.g.: behavioural description, gate
level netlist, etc.). While this flexibility is necessary for a language that
is used to represent circuits at multiple abstraction levels, it also requires
the designer to be explicit about every detail of their design.

High Level Synthesis (HLS) tools are trying to approach the digital design
problem from the opposite extreme. A high level (often C language based)
description of the design is given, and the synthesis tool derives the
necessary micro-architecture to implement the design, usually outputting the
result in a standard low level HDL. The high level input language can sometimes
make it difficult for the designer to express specific micro-architectural
intent.

Alogic was motivated by the belief that there is a gap between these two
approaches to digital design specification. Alogic retains the ability to
specify the micro-architecture of digital designs explicitly, but provides
specialized syntax for the description of commonly used hardware structures,
and hence raises the level of abstraction to a medium level. The goal of Alogic
is not to try to derive the necessary architecture from a high-level
description (this is left to the expertise of the designer), but to allow the
explicit and concise expression of the most commonly used digital structures.
After reading the documentation, the designer should be able to tell exactly
what kind of hardware structure their Alogic design is going to compile into.
The Alogic compiler will then generate a SystemVerilog description of the
design, which conforms to the common RTL best practice guidelines. Compilation
of Alogic to SystemVerilog takes a similar amount of time as a similar
complexity C program would take to compile into an executable.

## Further information

* [Documentation](doc/index.md)
* [Online compiler playground](https://alogic.app)
* [Installation](doc/install.md)
* [License](LICENSE)
