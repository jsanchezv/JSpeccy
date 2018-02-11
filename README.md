# JSpeccy
### A multiplatform ZX Spectrum emulator written in Java language.

* Emulates ZX Spectrum models 16k, 48k, 128k, +2 and +2a
* Uses the same Z80 core as [Z80Core](http://github.com/jsanchezv/Z80Core) project.
* Contended memory emulation.
* Border effects (Aquaplane, The Sentinel)
* Selectable border size (no border, standard, complete, huge)
* High resolution color effects (Uridium, Black Lamp)
* Emulation for screen scanlines, PAL effect or RGB monitor
* Floating bus emulation (Arkanoid, Sidewize, Cobra)
* Beeper & MIC emulation (Cobra's Arc)
* Read/Write SNA/Z80/SP/SZX snapshot formats
* LOAD/SAVE Tape files in TAP/TZX/CSW formats
* Emulates Kempston, Sinclair 1/2, Cursor AGF, Fuller joysticks using keyboard cursor keys.
* AY-3-8910 PSG emulation, including Fuller Audio Box, with Mono & Stereo ABC/ACB/BAC modes.
* Interfaz I with up to 8 Microdrives, using real emulation when using MDV files.
* Interfaz II ROM emulation.
* Multiface One/128/Plus 3 emulation
* ULA+ mode support (up to 64 colors on screen)
* LEC Memory expansion, up to 528 KB, to use LEC CP/M v2.2, using Microdrives as storage
* Window can be maximized up to x4.
* Selectable emulation speed up to x10 (35 Mhz CPU)
* Translations to English, Spanish & Italian
* Complete command line support, to integrate JSpeccy with front-ends.

### How to Use
You need to have installed Java 8. Run with:

**java -jar JSpeccy.jar**

A configuration file named JSpeccy.xml will be created on the user directory.

On Unix/Linux platforms using X11, Java 8 have a bug redrawing the screen. Java 8 use
the XRender extension by default and this causes some problems. To resolve it, you can
test two possible solutions. First, you can add the option:

**java -Dsun.java2d.opengl=True -jar JSpeccy.jar**

that uses the OpenGL backend. This solution can be problematic when don't exist a
good OpenGL driver or X11 is using Mesa. With these case you can use:

**java -Dsun.java2d.xrender=false -jar JSpeccy.jar**

If you are using Java 9 you need to add another startup option:

**java --add-modules jdk.xml.bind -jar JSpeccy.jar**

in Java 9 the Swing redrawing bug exist too, and you can need any of the previous
solutions (sigh!).

Web: [JSpeccy](http://jspeccy.speccy.org) (only in Spanish, I'm sorry)
