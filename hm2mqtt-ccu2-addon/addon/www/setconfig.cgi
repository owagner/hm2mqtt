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

        set filename "/usr/local/addons/hm2mqtt/options"
        set fp [open $filename "w"]

        puts -nonewline $fp [read stdin]

        close $fp

        puts "settings saved"

    } else {
        puts {error: session invalid}
    }

} else {
    puts {error: no session}
}




