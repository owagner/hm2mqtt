#!/bin/tclsh

load tclrega.so

catch {
  set input $env(QUERY_STRING)
  set pairs [split $input &]
  foreach pair $pairs {
    if {0 != [regexp "^(\[^=]*)=(.*)$" $pair dummy varname val]} {
      set $varname $val
    }
  }
}

if {[info exists sid] > 0} {
    # Session prüfen
    regsub -all {@} $sid "" sid
    set res [lindex [rega_script "Write(system.GetSessionVarStr('$sid'));"] 1]
    if {$res != ""} {
        # gültige Session

        catch {exec /usr/local/etc/config/rc.d/hm2mqtt start} result
        puts $result

    } else {
        puts {error: session invalid}
    }

} else {
    puts {error: no session}
}



