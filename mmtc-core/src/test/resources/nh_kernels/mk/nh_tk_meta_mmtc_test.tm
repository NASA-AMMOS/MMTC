KPL/MK

New Horizons Meta (FURNSH) Kernel
==============================================================================

   This kernel contains a list of SPICE kernels to load with the
   New Horizons Timekeeping system.
 
Version and Date
----------------------------------------------------------

   Version 1.1.0 -- August 18, 2014

            -- Modified to include revised Earth stations SPK created by S. Turner
	    and the dss_35_36_prelim_itrf93_140620 which provides station ID bindings
	    for the new DSS-35 and DSS-36 stations.


   Version 1.0.0 -- January 10, 2006

            -- This metakernel is provided for operational
            -- use by the New Horizons Timekeeping system.

References
----------------------------------------------------------

            1. ``Kernel Pool Required Reading''

            2. furnsh_c (CSPICE) or FURNSH (SPICELIB) module headers.

Implementation Notes
----------------------------------------------------------

    This file is a SPICE meta-kernel used by the SPICE system as follows:
    programs that make use of this kernel must ``load'' it, normally during
    program initialization. Loading the kernel loads all of the kernels 
    referenced in the KERNELS_TO_LOAD keyword below.  The SPICELIB routine
    FURNSH and CSPICE routine furnsh_c load this kernel as illustrated
    below:

    FORTRAN (SPICELIB)

             CALL FURNSH ( 'meta_kernel_name' )

    C (CSPICE)

             furnsh_c ( "meta_kernel_name" )

    This meta-kernel assumes that the kernels are located in a directory
    structure as follows:

       path-to-kernels/
                    |
                    ck/
                    fk/
                    ik/
                    lsk/
                    pck/
                    sclk/
                    spk/

    If one maintains a duplicate of the officially distributed kernel
    tree on their system, then one must only edit the path symbol/name
    pair described in the section below to point to their local kernel
    "$ROOT" path.       

    This file was created and may be updated with a text editor or word 
    processor.

Root Path Definition
----------------------------------------------------------

    As discussed above, this meta-kernel assumes that all (or most) of
    the kernels to be loaded are located under a particular directory.
    The following PATH_SYMBOL/PATH_NAME pair defines the top level or
    "ROOT" directory underneath which most of the kernels lie.

       \begindata

          PATH_SYMBOLS += ( 'ROOT' )
          PATH_VALUES  += ( 'src/test/resources/nh_kernels' )
          

       \begintext

    If you have a duplicate kernel tree located somewhere else on your
    system, you can simply change the value of PATH_VALUES above to point
    it at your local repository.

Kernel Path Definitions
----------------------------------------------------------

    These are the names of the directories underneath which individual
    kernel types are sorted.  See the discussion above in the Implementation
    Notes section for more details on the path structure.

       \begindata

          PATH_SYMBOLS += ( 'FK'
                            'IK'
                            'LSK'
                            'PCK'
                            'SCLK'
                            'SPK'    )

          PATH_VALUES  += ( 'fk'
                            'ik'
                            'lsk'
                            'pck'
                            'sclk'
                            'spk'        )

       \begintext

Kernels to Load
----------------------------------------------------------

    The following is the list of kernels to load.  They are broken
    up into types with a brief explanation of what each kernel 
    provides to the SPICE system when it is loaded.
    
    Note that the SCLK kernel is not included here as it will be
    loaded based upon configuration information provided for each
    specific timekeeping installation.

Planetary Constants Kernel:

    This kernel provides information about body-fixed frames, triaxial
    shape models, and a variety of other generic information about planets,
    moons, and other solar system bodies.

       \begindata

          KERNELS_TO_LOAD += ( '$ROOT/$PCK/pck00010.tpc' 
                               '$ROOT/$PCK/earth_070425_370426_predict.bpc' )

       \begintext

Spacecraft and Planetary Ephemerides:

    These kernels provide information about the states of New Horizons
    and other solar system bodies.

    Eliding jup260.bsp here due to size.

       \begindata 

          KERNELS_TO_LOAD += ( '$ROOT/$SPK/earthstns_fx_050714.bsp'
                               '$ROOT/$SPK/dss_35_36_prelim_fx_140620.bsp'
                               '$ROOT/$SPK/sb-2014mu69-20150903_s6.bsp'
                               '$ROOT/$SPK/NavSE_plu047_od123.bsp'
                               '$ROOT/$SPK/NavPE_de433_od123.bsp'
                               '$ROOT/$SPK/nh_pred_20150801_20190301_od124.bsp'
                               '$ROOT/$SPK/nh_recon_pluto_od122_v01.bsp'   )

       \begintext

Frame:

    The dss_35_36_prelim_itrf93_140620.tf kernel provides station ID code bindings
    for DSS-35 and DSS-36 that are needed in the earth stations SPK.


       \begindata 

          KERNELS_TO_LOAD += ( '$ROOT/$FK/dss_35_36_prelim_itrf93_140620.tf'
                               '$ROOT/$FK/earth_fixed.tf'
                               '$ROOT/$FK/earth_topo_050714.tf'  )

       \begintext
