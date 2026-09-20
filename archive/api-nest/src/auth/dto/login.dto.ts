import { ApiProperty } from '@nestjs/swagger';
import { IsString, MinLength, MaxLength } from 'class-validator';

export class LoginDto {
  @ApiProperty({ example: 'admin@example.com' })
  @IsString()
  @MinLength(3)
  @MaxLength(254)
  account!: string;

  @ApiProperty({ example: 'MiniCrm-Dev-Only-ChangeMe', writeOnly: true })
  @IsString()
  @MinLength(8)
  @MaxLength(128)
  password!: string;
}
