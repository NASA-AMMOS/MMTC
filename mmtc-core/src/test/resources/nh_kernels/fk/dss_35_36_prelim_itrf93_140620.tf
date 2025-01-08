KPL/FK
 
   FILE: dss_35_36_prelim_itrf93_140620.tf
 
   This file was created by PINPOINT.
 
   PINPOINT Version 3.0.0 --- March 26, 2009
   PINPOINT RUN DATE/TIME:    2014-06-20T19:01:17
   PINPOINT DEFINITIONS FILE: 35_36.inp
   PINPOINT PCK FILE:         dsn.tpc
   PINPOINT SPK FILE:         dss_35_36_prelim_itrf93_140620.bsp
 
   The input definitions file is appended to this
   file as a comment block.
 
 
   Body-name mapping follows:
 
\begindata
 
   NAIF_BODY_NAME                      += 'DSS-35'
   NAIF_BODY_CODE                      += 399035
 
   NAIF_BODY_NAME                      += 'DSS-36'
   NAIF_BODY_CODE                      += 399036
 
\begintext
 
 
   Reference frame specifications follow:
 
 
   Topocentric frame DSS-35_TOPO
 
      The Z axis of this frame points toward the zenith.
      The X axis of this frame points North.
 
      Topocentric frame DSS-35_TOPO is centered at the site DSS-35
      which at the epoch
 
          2003 JAN 01 00:00:00.000 TDB
 
       has Cartesian coordinates
 
         X (km):                 -0.4461273083800E+04
         Y (km):                  0.2682568922000E+04
         Z (km):                 -0.3674152088500E+04
 
      and planetodetic coordinates
 
         Longitude (deg):       148.9814557600491
         Latitude  (deg):       -35.3957956324818
         Altitude   (km):         0.6955933961813E+00
 
      These planetodetic coordinates are expressed relative to
      a reference spheroid having the dimensions
 
         Equatorial radius (km):  6.3781363000000E+03
         Polar radius      (km):  6.3567516005629E+03
 
      All of the above coordinates are relative to the frame ITRF93.
 
 
\begindata
 
   FRAME_DSS-35_TOPO                   =  1399035
   FRAME_1399035_NAME                  =  'DSS-35_TOPO'
   FRAME_1399035_CLASS                 =  4
   FRAME_1399035_CLASS_ID              =  1399035
   FRAME_1399035_CENTER                =  399035
 
   OBJECT_399035_FRAME                 =  'DSS-35_TOPO'
 
   TKFRAME_1399035_RELATIVE            =  'ITRF93'
   TKFRAME_1399035_SPEC                =  'ANGLES'
   TKFRAME_1399035_UNITS               =  'DEGREES'
   TKFRAME_1399035_AXES                =  ( 3, 2, 3 )
   TKFRAME_1399035_ANGLES              =  ( -148.9814557600491,
                                            -125.3957956324818,
                                             180.0000000000000 )
 
 
\begintext
 
   Topocentric frame DSS-36_TOPO
 
      The Z axis of this frame points toward the zenith.
      The X axis of this frame points North.
 
      Topocentric frame DSS-36_TOPO is centered at the site DSS-36
      which has Cartesian coordinates
 
         X (km):                 -0.4461170235800E+04
         Y (km):                  0.2682816024000E+04
         Z (km):                 -0.3674085973700E+04
 
      and planetodetic coordinates
 
         Longitude (deg):       148.9785416670200
         Latitude  (deg):       -35.3951052825684
         Altitude   (km):         0.6892545905352E+00
 
      These planetodetic coordinates are expressed relative to
      a reference spheroid having the dimensions
 
         Equatorial radius (km):  6.3781363000000E+03
         Polar radius      (km):  6.3567516005629E+03
 
      All of the above coordinates are relative to the frame ITRF93.
 
 
\begindata
 
   FRAME_DSS-36_TOPO                   =  1399036
   FRAME_1399036_NAME                  =  'DSS-36_TOPO'
   FRAME_1399036_CLASS                 =  4
   FRAME_1399036_CLASS_ID              =  1399036
   FRAME_1399036_CENTER                =  399036
 
   OBJECT_399036_FRAME                 =  'DSS-36_TOPO'
 
   TKFRAME_1399036_RELATIVE            =  'ITRF93'
   TKFRAME_1399036_SPEC                =  'ANGLES'
   TKFRAME_1399036_UNITS               =  'DEGREES'
   TKFRAME_1399036_AXES                =  ( 3, 2, 3 )
   TKFRAME_1399036_ANGLES              =  ( -148.9785416670200,
                                            -125.3951052825684,
                                             180.0000000000000 )
 
\begintext
 
 
Definitions file 35_36.inp
--------------------------------------------------------------------------------
 
 
   SPK/FK for Preliminary DSS-35, DSS-36 Station Locations
   =====================================================================
 
   Original SPK file name:               dss_35_36_prelim_itrf93_140620.bsp
   Original FK file name:                dss_35_36_prelim_itrf93_140620.tf
   Creation date:                        2014 June 20 18:59
   Created by:                           Nat Bachman  (NAIF/JPL)
 
 
 
   Data for DSS-35 are based on an email from Dr. William Folkner,
   dated June 13, 2014. The position data from that email are shown
   below:
 
     >> Cartesian coordinates x, y, z (m)
     >>
     >> -4461273.0838  2682568.9220 -3674152.0885
 
   The email states that the position is accurate to about 1 cm.
   It also states that the site velocity for other stations in
   the Canberra complex applies to DSS-35. This velocity is now
   included in the SPK file for this station. The epoch of the data
   is 2003.0. The velocity has been transformed to the ITRF93
   reference frame from the DSS-35_TOPO frame, and it has been
   scaled to units of m/year. The original velocity data are
   included at the end of this file.
 
   Topocentric frame orientations were derived using earth radii
   included below.
 
 
   Data for DSS-36 are based on an email from Dr. William Folkner,
   dated May 28, 2014. The position data from that email are shown
   below:
 
      >   Cartesian coordinates (m)
      >36 DSS-36         -4461170.2358   2682816.0240  -3674085.9737
 
   Note that site velocity data are not included.
 
   Topocentric frame orientations were derived using earth radii
   included below.
 
 
begindata
 
 
   SITES              +=      'DSS-35'
   DSS-35_FRAME       =       'ITRF93'
   DSS-35_CENTER      =       399
   DSS-35_IDCODE      =       399035
   DSS-35_BOUNDS      =    (  @1950-JAN-01/00:00,  @2050-JAN-01/00:00  )
   DSS-35_XYZ         =    (  -4461.2730838
                               2682.5689220
                              -3674.1520885 )
 
   DSS-35_DXYZ        =    (   -3.340926970650297e-02
                               -4.181661498524273e-03
                                3.933413790576708e-02 )
 
   DSS-35_EPOCH       =     @2003-JAN-01/00:00:00
   DSS-35_TOPO_EPOCH  =     @2003-JAN-01/00:00:00
 
   DSS-35_UP          =     'Z'
   DSS-35_NORTH       =     'X'
 
 
 
   SITES           +=      'DSS-36'
   DSS-36_FRAME    =       'ITRF93'
   DSS-36_CENTER   =       399
   DSS-36_IDCODE   =       399036
   DSS-36_BOUNDS   =    (  @1950-JAN-01/00:00,  @2050-JAN-01/00:00  )
   DSS-36_XYZ      =    (    -4461.1702358
                              2682.8160240
                             -3674.0859737  )
   DSS-36_UP       =  'Z'
   DSS-36_NORTH    =  'X'
 
begintext
 
   Earth radii for DSN kernel generation
   =====================================
 
   Author:                                        Nat Bachman
   File creation date:                            03-JUN-2014
 
 
   Reference Spheroid
   ------------------
 
   The reference bi-axial spheroid is defined by an equatorial and a
   polar radius.  Calling these Re and Rp respectively, the flattening
   factor f is defined as
 
      f = ( Re - Rp ) / Re
 
   For the reference spheroid used by this file, the equatorial radius
   Re and inverse flattening factor 1/f are
 
      Re         = 6378136.3 m
      1/f        = 298.257
 
      Derived Rp = 6356.7516005629377
 
begindata
 
   BODY399_RADII = ( 6378.1363, 6378.1363, 6356.7516005629377 )
 
begintext
 
 
   Velocity data
   -------------
 
   The relevant portion of the ODP input file providing
   station velocities is copied (and reformatted) below.
 
 
       Reference epoch for plate motion: 01-JAN-2003 00:00
 
       Plate motion model, cm/year
 
                                     North   East    Vertical
 
       Canberra:
 
          Stations numbered 3X        4.74    2.08    -0.12
 
 
begintext
 
[End of definitions file]
 
